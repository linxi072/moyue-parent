package com.moyue.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.member.client.MemberPaymentGateway;
import com.moyue.member.entity.MemberSubscriptionEntity;
import com.moyue.member.entity.MemberTierEntity;
import com.moyue.member.mapper.MemberSubscriptionMapper;
import com.moyue.member.mapper.MemberTierMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 会员订阅业务：套餐查询 / 开通订阅（支付网关扣款 + 状态机）/ 到期自动降级 / 权益计算 / 连续订阅（自动续费）。
 * 支付外部依赖经 {@link MemberPaymentGateway} 配置驱动降级（默认 Stub 空跑，同 P1 风格），不阻断业务主链路。
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    @Autowired
    private MemberTierMapper tierMapper;

    @Autowired
    private MemberSubscriptionMapper subscriptionMapper;

    @Autowired
    private MemberPaymentGateway paymentGateway;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ---------------------------------------------------------------
    // 套餐
    // ---------------------------------------------------------------

    /** 在售套餐列表（未删除，按排序升序） */
    public List<MemberTierEntity> listOnSaleTiers() {
        LambdaQueryWrapper<MemberTierEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberTierEntity::getIsDeleted, 0).orderByAsc(MemberTierEntity::getSort);
        return tierMapper.selectList(wrapper);
    }

    // ---------------------------------------------------------------
    // 订阅
    // ---------------------------------------------------------------

    /**
     * 开通会员订阅：创建「待支付」订阅单 → 经支付网关扣款 → 成功则激活并写入有效期。
     * 支付网关默认 Stub（空跑）始终成功；接真实渠道后失败会抛 PAYMENT_FAILED 并标记订阅已取消。
     * 续费字段在此快照：renewCycleDays 取套餐有效期（整期续费依据），autoRenew=false、renewFailCount=0。
     */
    @Transactional
    public MemberSubscriptionEntity subscribe(Long userId, String tierCode) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        MemberTierEntity tier = findTier(tierCode);
        if (tier == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "套餐不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        MemberSubscriptionEntity sub = new MemberSubscriptionEntity();
        sub.setUserId(userId);
        sub.setTierCode(tier.getTierCode());
        sub.setTierName(tier.getTierName());
        sub.setStatus(SubscriptionStateMachine.STATUS_PENDING);
        sub.setOrderNo(generateOrderNo());
        sub.setIsDeleted(0);
        // P2-B 续费快照：周期取套餐有效期；开关默认关、失败计数 0（避免 NOT NULL 列插入 NULL）
        sub.setAutoRenew(false);
        sub.setRenewCycleDays(tier.getDurationDays() == null ? 30 : tier.getDurationDays());
        sub.setRenewFailCount(0);
        sub.setCreateTime(now);
        sub.setUpdateTime(now);
        subscriptionMapper.insert(sub);

        MemberPaymentGateway.ChargeResult result =
                paymentGateway.charge(userId, tier.getMonthlyPrice(), sub.getOrderNo());
        if (!result.isSuccess()) {
            // 支付失败：标记已取消，订阅不生效（不抛阻断——调用方可据状态判断）
            sub.setStatus(SubscriptionStateMachine.STATUS_CANCELLED);
            sub.setUpdateTime(LocalDateTime.now());
            subscriptionMapper.updateById(sub);
            throw new BizException(ResultCode.PAYMENT_FAILED);
        }

        sub.setStatus(SubscriptionStateMachine.STATUS_ACTIVE);
        sub.setStartTime(now);
        sub.setEndTime(now.plusDays(tier.getDurationDays() == null ? 30 : tier.getDurationDays()));
        sub.setPaySerial(result.getPaySerial());
        sub.setChannel(result.getChannel());
        sub.setUpdateTime(LocalDateTime.now());
        subscriptionMapper.updateById(sub);
        return sub;
    }

    /**
     * 自动续费开关：校验本人 + 未删订阅，设置 autoRenew；开启时按窗口回填 renewAt，关闭时清空。
     * renewAt 窗口 = max(1, renewCycleDays/3) 天（从 endTime 向前推）。
     */
    @Transactional
    public void setAutoRenew(Long userId, Long subscriptionId, boolean enable) {
        if (userId == null || subscriptionId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID / 订阅 ID 不能为空");
        }
        MemberSubscriptionEntity sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "订阅不存在");
        }
        if (!sub.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能操作本人订阅");
        }
        sub.setAutoRenew(enable);
        if (enable) {
            int cycle = sub.getRenewCycleDays() == null ? 30 : sub.getRenewCycleDays();
            int window = Math.max(1, cycle / 3);
            LocalDateTime baseEnd = sub.getEndTime() != null ? sub.getEndTime() : LocalDateTime.now();
            sub.setRenewAt(baseEnd.minusDays(window));
        } else {
            sub.setRenewAt(null);
        }
        sub.setUpdateTime(LocalDateTime.now());
        subscriptionMapper.updateById(sub);
    }

    /**
     * 续费单条订阅（事务内）：扣款 → 成功则条件延长有效期（原记录整期续费，幂等），失败则标记续费失败、保持 active 不延长。
     *
     * @return 是否「真实成功」（success &amp;&amp; realSuccess）；失败 / 降级成功 / 异常均返回 false
     */
    @Transactional
    public boolean renewSubscription(MemberSubscriptionEntity sub, MemberTierEntity tier) {
        try {
            // 续费业务单号：供真实网关侧幂等去重（防 Job 重复执行双扣）
            String bizNo = "RENEW-" + sub.getId() + "-" + renewAtEpoch(sub);
            MemberPaymentGateway.ChargeResult result = paymentGateway.charge(sub.getUserId(), tier.getMonthlyPrice(), bizNo);
            LocalDateTime now = LocalDateTime.now();

            if (result != null && result.isSuccess() && result.isRealSuccess()) {
                // 真实扣款成功：整期延长 endTime，并前移 renewAt，乐观更新（renew_at 已过期才生效，0 行跳过）
                int cycle = sub.getRenewCycleDays() == null ? 30 : sub.getRenewCycleDays();
                int window = Math.max(1, cycle / 3);
                LocalDateTime baseEnd = sub.getEndTime() != null ? sub.getEndTime() : now;
                LocalDateTime newEndTime = baseEnd.plusDays(cycle);
                LocalDateTime newRenewAt = newEndTime.minusDays(window);

                LambdaUpdateWrapper<MemberSubscriptionEntity> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(MemberSubscriptionEntity::getId, sub.getId())
                        .eq(MemberSubscriptionEntity::getStatus, SubscriptionStateMachine.STATUS_ACTIVE)
                        .eq(MemberSubscriptionEntity::getIsDeleted, 0)
                        .le(MemberSubscriptionEntity::getRenewAt, now)
                        .set(MemberSubscriptionEntity::getEndTime, newEndTime)
                        .set(MemberSubscriptionEntity::getRenewAt, newRenewAt)
                        .set(MemberSubscriptionEntity::getLastRenewAt, now)
                        .set(MemberSubscriptionEntity::getRenewLastStatus, 0)
                        .set(MemberSubscriptionEntity::getRenewFailCount, 0)
                        .set(MemberSubscriptionEntity::getUpdateTime, now);
                subscriptionMapper.update(null, wrapper);
                return true;
            }

            // 网关降级成功 / 判定失败：标记续费失败、保持 active、不延长 endTime
            markRenewFailed(sub, renewFailReason(result), now);
            return false;
        } catch (Exception e) {
            // 单条续费异常：标记失败、保持 active、绝不抛出
            log.error("会员续费单条异常 userId={} subId={}", sub.getUserId(), sub.getId(), e);
            try {
                markRenewFailed(sub, "续费异常：" + (e.getMessage() == null ? "unknown" : e.getMessage()), LocalDateTime.now());
            } catch (Exception ignore) {
                // 至多记日志，避免异常逃逸
            }
            return false;
        }
    }

    /**
     * 续费待处理扫描：分页扫描「生效中 + 已开自动续费 + renew_at 已过」的订阅，逐条续费。
     * 单条异常仅记日志、继续后续，绝不抛出；返回成功（真实扣款）续费条数。
     */
    @Transactional
    public int renewDueSubscriptions() {
        int processed = 0;
        LocalDateTime now = LocalDateTime.now();
        final int pageSize = 100;
        int page = 0;
        while (true) {
            LambdaQueryWrapper<MemberSubscriptionEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MemberSubscriptionEntity::getStatus, SubscriptionStateMachine.STATUS_ACTIVE)
                    .eq(MemberSubscriptionEntity::getAutoRenew, true)
                    .le(MemberSubscriptionEntity::getRenewAt, now)
                    .eq(MemberSubscriptionEntity::getIsDeleted, 0)
                    .orderByAsc(MemberSubscriptionEntity::getRenewAt)
                    .last("LIMIT " + pageSize + " OFFSET " + (page * pageSize));
            List<MemberSubscriptionEntity> due = subscriptionMapper.selectList(wrapper);
            if (due == null || due.isEmpty()) {
                break;
            }
            for (MemberSubscriptionEntity sub : due) {
                try {
                    MemberTierEntity tier = findTier(sub.getTierCode());
                    if (tier == null) {
                        log.warn("会员续费跳过：套餐不存在 userId={} subId={} tierCode={}",
                                sub.getUserId(), sub.getId(), sub.getTierCode());
                        continue;
                    }
                    if (renewSubscription(sub, tier)) {
                        processed++;
                    }
                } catch (Exception e) {
                    // 单条异常仅记日志，继续处理后续订阅，绝不抛出
                    log.error("会员续费处理异常（已跳过该订阅） userId={} subId={}",
                            sub.getUserId(), sub.getId(), e);
                }
            }
            if (due.size() < pageSize) {
                break;
            }
            page++;
        }
        return processed;
    }

    /**
     * 会员中心聚合视图：当前等级 / 实时权益 / 生效订阅快照 / 自动续费开关 / 可升级套餐。
     * 非会员亦返回（activeSubscription=null），不抛、不阻断。
     */
    public MemberCenterView getMemberCenter(Long userId) {
        MemberCenterView view = new MemberCenterView();
        view.setUserId(userId);

        MemberBenefits benefits = getBenefits(userId);
        view.setBenefits(benefits);
        view.setMember(benefits.isActive());

        MemberSubscriptionEntity active = getActiveSubscription(userId);
        if (active != null) {
            view.setCurrentTierName(active.getTierName());
            view.setAutoRenew(Boolean.TRUE.equals(active.getAutoRenew()));

            MemberSubscriptionView sv = new MemberSubscriptionView();
            sv.setId(active.getId());
            sv.setTierCode(active.getTierCode());
            sv.setTierName(active.getTierName());
            sv.setStatus(active.getStatus());
            sv.setStartTime(active.getStartTime());
            sv.setEndTime(active.getEndTime());
            sv.setAutoRenew(Boolean.TRUE.equals(active.getAutoRenew()));
            sv.setRenewAt(active.getRenewAt());
            sv.setLastRenewAt(active.getLastRenewAt());
            sv.setRenewLastStatus(active.getRenewLastStatus());
            sv.setRenewLastMsg(active.getRenewLastMsg());
            view.setActiveSubscription(sv);
        } else {
            view.setCurrentTierName(null);
            view.setAutoRenew(false);
            view.setActiveSubscription(null);
        }

        List<TierUpgradeView> upgradeViews = new ArrayList<>();
        for (MemberTierEntity t : listUpgradeableTiers(active == null ? null : active.getTierCode())) {
            upgradeViews.add(toTierUpgradeView(t));
        }
        view.setUpgradeableTiers(upgradeViews);
        return view;
    }

    /**
     * 可升级套餐：在售套餐中排除当前套餐（同一 module 展示排序更高的即为可升级）。
     * currentTierCode 为空（非会员）返回全部在售套餐。
     */
    public List<MemberTierEntity> listUpgradeableTiers(String currentTierCode) {
        List<MemberTierEntity> onSale = listOnSaleTiers();
        if (currentTierCode == null || currentTierCode.isBlank()) {
            return onSale;
        }
        List<MemberTierEntity> result = new ArrayList<>();
        for (MemberTierEntity t : onSale) {
            if (!currentTierCode.equals(t.getTierCode())) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * 到期自动降级：把 end_time 已过且仍「生效中」的订阅翻为「已过期」。
     * 返回影响行数；可由定时任务或读权益时顺带触发（本实现读权益前会先同步一次）。
     */
    @Transactional
    public int syncExpired() {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MemberSubscriptionEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MemberSubscriptionEntity::getStatus, SubscriptionStateMachine.STATUS_ACTIVE)
                .lt(MemberSubscriptionEntity::getEndTime, now)
                .set(MemberSubscriptionEntity::getStatus, SubscriptionStateMachine.STATUS_EXPIRED)
                .set(MemberSubscriptionEntity::getUpdateTime, now);
        return subscriptionMapper.update(null, wrapper);
    }

    /** 当前用户生效中的订阅（先同步一次过期态，再查最新有效行） */
    public MemberSubscriptionEntity getActiveSubscription(Long userId) {
        syncExpired();
        LambdaQueryWrapper<MemberSubscriptionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberSubscriptionEntity::getUserId, userId)
                .eq(MemberSubscriptionEntity::getIsDeleted, 0)
                .eq(MemberSubscriptionEntity::getStatus, SubscriptionStateMachine.STATUS_ACTIVE)
                .gt(MemberSubscriptionEntity::getEndTime, LocalDateTime.now())
                .orderByDesc(MemberSubscriptionEntity::getEndTime)
                .last("LIMIT 1");
        return subscriptionMapper.selectOne(wrapper);
    }

    /** 订阅历史（未删除，按下单时间倒序） */
    public List<MemberSubscriptionEntity> listSubscriptions(Long userId) {
        LambdaQueryWrapper<MemberSubscriptionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberSubscriptionEntity::getUserId, userId)
                .eq(MemberSubscriptionEntity::getIsDeleted, 0)
                .orderByDesc(MemberSubscriptionEntity::getCreateTime);
        return subscriptionMapper.selectList(wrapper);
    }

    /** 取消订阅（仅本人、且待支付/生效中可取消，经状态机校验） */
    @Transactional
    public void cancelSubscription(Long userId, Long subscriptionId) {
        MemberSubscriptionEntity sub = subscriptionMapper.selectById(subscriptionId);
        if (sub == null || (sub.getIsDeleted() != null && sub.getIsDeleted() == 1)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (!sub.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能取消本人订阅");
        }
        sub.setStatus(SubscriptionStateMachine.cancel(sub.getStatus()));
        sub.setUpdateTime(LocalDateTime.now());
        subscriptionMapper.updateById(sub);
    }

    /** 计算当前用户会员权益（免广告 / 折扣 / 徽章） */
    public MemberBenefits getBenefits(Long userId) {
        MemberSubscriptionEntity active = getActiveSubscription(userId);
        MemberBenefits benefits = new MemberBenefits();
        benefits.setActive(active != null);
        if (active == null) {
            benefits.setTierName(null);
            benefits.setAdFree(false);
            benefits.setDiscountRate(BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
            benefits.setBadges(new ArrayList<>());
            return benefits;
        }
        benefits.setTierName(active.getTierName());
        MemberTierEntity tier = findTier(active.getTierCode());
        if (tier != null) {
            benefits.setAdFree(tier.getAdFree() != null && tier.getAdFree() == 1);
            benefits.setDiscountRate(tier.getDiscountRate() == null
                    ? BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP)
                    : tier.getDiscountRate().setScale(2, RoundingMode.HALF_UP));
            List<String> badges = new ArrayList<>();
            if (tier.getBadge() != null && !tier.getBadge().isBlank()) {
                badges.add(tier.getBadge());
            }
            benefits.setBadges(badges);
        } else {
            benefits.setAdFree(false);
            benefits.setDiscountRate(BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
            benefits.setBadges(new ArrayList<>());
        }
        return benefits;
    }

    /** 当前用户会员折扣率（无会员 / 无折扣返回 1.00） */
    public BigDecimal getDiscountRate(Long userId) {
        MemberBenefits b = getBenefits(userId);
        return b.isActive() && b.getDiscountRate() != null
                ? b.getDiscountRate() : BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
    }

    /** 当前用户会员专属徽章（无会员 / 无徽章返回 null，取首个） */
    public String getBadge(Long userId) {
        MemberBenefits b = getBenefits(userId);
        if (b.isActive() && b.getBadges() != null && !b.getBadges().isEmpty()) {
            return b.getBadges().get(0);
        }
        return null;
    }

    // ---------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------

    private void markRenewFailed(MemberSubscriptionEntity sub, String reason, LocalDateTime now) {
        sub.setRenewLastStatus(1);
        sub.setRenewFailCount((sub.getRenewFailCount() == null ? 0 : sub.getRenewFailCount()) + 1);
        sub.setRenewLastMsg(reason);
        sub.setUpdateTime(now);
        subscriptionMapper.updateById(sub);
    }

    private String renewFailReason(MemberPaymentGateway.ChargeResult result) {
        if (result == null) {
            return "支付结果为空";
        }
        if (result.isSuccess()) {
            return "支付网关降级（真实扣款未成功）：" + (result.getChannel() == null ? "unknown" : result.getChannel());
        }
        return "支付失败：" + (result.getChannel() == null ? "unknown" : result.getChannel());
    }

    private long renewAtEpoch(MemberSubscriptionEntity sub) {
        LocalDateTime ra = sub.getRenewAt();
        if (ra == null) {
            return Instant.now().getEpochSecond();
        }
        return ra.toEpochSecond(ZoneOffset.UTC);
    }

    private MemberTierEntity findTier(String tierCode) {
        if (tierCode == null || tierCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<MemberTierEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberTierEntity::getTierCode, tierCode)
                .eq(MemberTierEntity::getIsDeleted, 0)
                .last("LIMIT 1");
        return tierMapper.selectOne(wrapper);
    }

    private String generateOrderNo() {
        StringBuilder sb = new StringBuilder(LocalDateTime.now().format(ORDER_TS));
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private TierUpgradeView toTierUpgradeView(MemberTierEntity t) {
        TierUpgradeView v = new TierUpgradeView();
        v.setTierCode(t.getTierCode());
        v.setTierName(t.getTierName());
        v.setMonthlyPrice(t.getMonthlyPrice());
        v.setDurationDays(t.getDurationDays());
        v.setAdFree(t.getAdFree());
        v.setDiscountRate(t.getDiscountRate());
        v.setBadge(t.getBadge());
        return v;
    }

    // ---------------------------------------------------------------
    // 出参视图
    // ---------------------------------------------------------------

    /** 会员权益视图 */
    @Data
    public static class MemberBenefits {
        /** 是否会员生效中 */
        private boolean active;
        /** 套餐名称 */
        private String tierName;
        /** 免广告 */
        private boolean adFree;
        /** 折扣率（1.00 无折扣） */
        private BigDecimal discountRate;
        /** 专属徽章列表 */
        private List<String> badges;
    }

    /** 会员中心聚合视图 */
    @Data
    public static class MemberCenterView {
        /** 用户 ID */
        private Long userId;
        /** 是否会员生效中 */
        private boolean member;
        /** 当前等级（套餐名），非会员 null */
        private String currentTierName;
        /** 实时权益（复用 MemberBenefits） */
        private MemberBenefits benefits;
        /** 生效中订阅快照（含续费可见字段），非会员 null */
        private MemberSubscriptionView activeSubscription;
        /** 是否自动续费（activeSubscription != null 时有效） */
        private boolean autoRenew;
        /** 可升级套餐列表 */
        private List<TierUpgradeView> upgradeableTiers;
    }

    /** 生效中订阅快照（会员中心用，含续费可见字段） */
    @Data
    public static class MemberSubscriptionView {
        private Long id;
        private String tierCode;
        private String tierName;
        private Integer status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean autoRenew;
        private LocalDateTime renewAt;
        private LocalDateTime lastRenewAt;
        private Integer renewLastStatus;
        private String renewLastMsg;
    }

    /** 可升级套餐视图 */
    @Data
    public static class TierUpgradeView {
        private String tierCode;
        private String tierName;
        private BigDecimal monthlyPrice;
        private Integer durationDays;
        private Integer adFree;
        private BigDecimal discountRate;
        private String badge;
    }
}

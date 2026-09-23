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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 会员订阅业务：套餐查询 / 开通订阅（支付网关扣款 + 状态机）/ 到期自动降级 / 权益计算。
 * 支付外部依赖经 {@link MemberPaymentGateway} 配置驱动降级（默认 Stub 空跑，同 P1 风格），
 * 不阻断业务主链路。
 */
@Service
public class MemberService {

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

    // ---------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------

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
}

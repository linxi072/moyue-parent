package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.operation.client.PayChannelGateway;
import com.moyue.operation.client.PayChannelGateway.PayResult;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.entity.SettlementOrderEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import com.moyue.operation.mapper.SettlementOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 稿酬结算单状态机（P0-1）。
 *
 * <p>状态：0 待结算 → 1 已结算待打款 → 2 已打款；打款失败 → 3 打款失败（可重试回到 2）。
 * 结算范围仅含打赏分成(incomeType=2) 与买断分成(incomeType=4)，不含订阅分成(incomeType=1)。</p>
 *
 * <p>幂等：createSettlement 对 (authorId, period) 仅生成一张 status<2 的结算单；已生成的流水通过
 * settlement_id 回写锁定，防止重复结算。payout 已 status=2（含 pay_serial）时直接返回，避免重复打款。</p>
 */
@Service
public class SettlementService {

    /** 状态取值统一以 {@link SettlementStateMachine} 为唯一来源，避免两处魔法值漂移 */
    /** 结算单状态：0 待结算 */
    private static final int STATUS_PENDING = SettlementStateMachine.STATUS_PENDING;
    /** 结算单状态：1 已结算待打款 */
    private static final int STATUS_AUDITED = SettlementStateMachine.STATUS_SETTLED;
    /** 结算单状态：2 已打款 */
    private static final int STATUS_PAID = SettlementStateMachine.STATUS_PAID;
    /** 结算单状态：3 打款失败 */
    private static final int STATUS_FAILED = SettlementStateMachine.STATUS_FAILED;

    /** 参与结算的稿酬流水类型：打赏分成 + 买断分成 */
    private static final List<Integer> SETTLE_INCOME_TYPES = List.of(2, 4);

    /** 打款渠道映射：网关返回渠道标识 → 持久化枚举（1微信/2支付宝），桩渠道默认微信占位 */
    private static final Map<String, Integer> CHANNEL_CODE = new HashMap<>();
    static {
        CHANNEL_CODE.put("wechat", 1);
        CHANNEL_CODE.put("alipay", 2);
    }

    @Autowired
    private SettlementOrderMapper settlementOrderMapper;

    @Autowired
    private AuthorIncomeMapper authorIncomeMapper;

    @Autowired
    private SettlementPayoutService payoutService;

    /**
     * 生成结算单：聚合作者指定周期的未结算稿酬（incomeType IN (2,4)），锁定对应流水。
     * 幂等：若同一 (authorId, period) 已存在 status<2 的结算单，直接返回已有单。
     */
    @Transactional
    public SettlementOrderEntity createSettlement(Long authorId, String period) {
        SettlementOrderEntity existing = settlementOrderMapper.selectOne(Wrappers.<SettlementOrderEntity>lambdaQuery()
                .eq(SettlementOrderEntity::getAuthorId, authorId)
                .eq(SettlementOrderEntity::getPeriod, period)
                .lt(SettlementOrderEntity::getStatus, STATUS_PAID));
        if (existing != null) {
            return existing;
        }

        List<AuthorIncomeEntity> unsettled = authorIncomeMapper.selectList(Wrappers.<AuthorIncomeEntity>lambdaQuery()
                .eq(AuthorIncomeEntity::getAuthorId, authorId)
                .eq(AuthorIncomeEntity::getSettleMonth, period)
                .isNull(AuthorIncomeEntity::getSettlementId)
                .in(AuthorIncomeEntity::getIncomeType, SETTLE_INCOME_TYPES));
        if (unsettled.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "该周期无可结算稿酬");
        }

        BigDecimal total = unsettled.stream()
                .map(AuthorIncomeEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        SettlementOrderEntity order = new SettlementOrderEntity();
        order.setId(IdWorker.getId());
        order.setAuthorId(authorId);
        order.setPeriod(period);
        order.setTotalAmount(total);
        order.setStatus(STATUS_PENDING);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        settlementOrderMapper.insert(order);

        // 回写 settlement_id 锁定流水，防止重复结算（仅锁定未结算且未锁定部分）
        UpdateWrapper<AuthorIncomeEntity> uw = new UpdateWrapper<>();
        uw.eq("author_id", authorId)
                .eq("settle_month", period)
                .isNull("settlement_id")
                .in("income_type", SETTLE_INCOME_TYPES)
                .set("settlement_id", order.getId());
        authorIncomeMapper.update(null, uw);

        return order;
    }

    /**
     * 审核通过：status 0 → 1，记录操作人与备注。
     */
    @Transactional
    public SettlementOrderEntity auditApprove(Long id, Long operatorId) {
        SettlementOrderEntity order = getByIdOrThrow(id);
        if (!Objects.equals(order.getStatus(), STATUS_PENDING)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "结算单当前状态不可审核");
        }
        order.setStatus(STATUS_AUDITED);
        order.setOperatorId(operatorId);
        order.setRemark("审核通过");
        order.setUpdateTime(LocalDateTime.now());
        settlementOrderMapper.updateById(order);
        return order;
    }

    /**
     * 打款：status 1 → 2，委托 SettlementPayoutService 调支付网关。
     * 幂等：已是 status=2（含 pay_serial）直接返回，避免重复打款；非已审核态拒绝。
     */
    @Transactional
    public SettlementOrderEntity payout(Long id) {
        SettlementOrderEntity order = getByIdOrThrow(id);
        if (Objects.equals(order.getStatus(), STATUS_PAID)) {
            return order;
        }
        if (!Objects.equals(order.getStatus(), STATUS_AUDITED)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "结算单未处于已审核状态，无法打款");
        }
        return doPayout(order);
    }

    /**
     * 重试打款：status 为 1 或 3 时重跑打款；已 2 直接返回；其余态（0）拒绝。
     */
    @Transactional
    public SettlementOrderEntity retryPayout(Long id) {
        SettlementOrderEntity order = getByIdOrThrow(id);
        if (Objects.equals(order.getStatus(), STATUS_PAID)) {
            return order;
        }
        if (!Objects.equals(order.getStatus(), STATUS_AUDITED)
                && !Objects.equals(order.getStatus(), STATUS_FAILED)) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "结算单当前状态不可重试打款");
        }
        return doPayout(order);
    }

    /** 标记打款失败：status → 3，备注写入失败原因。 */
    @Transactional
    public SettlementOrderEntity markFailed(Long id, String reason) {
        SettlementOrderEntity order = getByIdOrThrow(id);
        order.setStatus(STATUS_FAILED);
        order.setRemark(reason);
        order.setUpdateTime(LocalDateTime.now());
        settlementOrderMapper.updateById(order);
        return order;
    }

    /** 作者视角：结算单列表 */
    public List<SettlementOrderEntity> listByAuthor(Long authorId) {
        return settlementOrderMapper.selectList(Wrappers.<SettlementOrderEntity>lambdaQuery()
                .eq(SettlementOrderEntity::getAuthorId, authorId)
                .orderByDesc(SettlementOrderEntity::getCreateTime));
    }

    /** 管理端：全部结算单列表 */
    public List<SettlementOrderEntity> listAll() {
        return settlementOrderMapper.selectList(Wrappers.<SettlementOrderEntity>lambdaQuery()
                .orderByDesc(SettlementOrderEntity::getCreateTime));
    }

    /** 详情 */
    public SettlementOrderEntity getById(Long id) {
        return getByIdOrThrow(id);
    }

    // ------------------------------ 内部工具 ------------------------------

    /** 执行打款：调网关，按结果转移状态并写回渠道流水号；失败标记 3。 */
    private SettlementOrderEntity doPayout(SettlementOrderEntity order) {
        PayResult result = payoutService.payout(order.getAuthorId(), order.getTotalAmount());
        if (result == null || !result.isSuccess()) {
            order.setStatus(STATUS_FAILED);
            order.setRemark("打款失败：" + (result != null ? result.getChannel() : "unknown"));
            order.setUpdateTime(LocalDateTime.now());
            settlementOrderMapper.updateById(order);
            return order;
        }
        order.setStatus(STATUS_PAID);
        order.setPaySerial(result.getPaySerial());
        order.setPayChannel(resolveChannel(result.getChannel()));
        order.setRemark("打款成功");
        order.setUpdateTime(LocalDateTime.now());
        settlementOrderMapper.updateById(order);
        return order;
    }

    /** 渠道标识 → 持久化枚举（桩渠道默认微信占位） */
    private Integer resolveChannel(String channel) {
        Integer code = CHANNEL_CODE.get(channel);
        return code != null ? code : 1;
    }

    private SettlementOrderEntity getByIdOrThrow(Long id) {
        SettlementOrderEntity order = settlementOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "结算单不存在");
        }
        return order;
    }
}

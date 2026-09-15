package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.moyue.operation.client.PayChannelGateway.PayResult;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.entity.SettlementOrderEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import com.moyue.operation.mapper.SettlementOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SettlementService 状态机编排单元单测（Mockito 模拟 Mapper / 打款服务，无真实 DB）。
 *
 * <p>覆盖：聚合生成(0) 并批量锁定流水 → 审核(1) → 打款(2)；
 * 打款失败(3) → 重试回到 2；已打款(2) 再调打款直接返回（幂等，不重复出款）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementOrderMapper settlementOrderMapper;

    @Mock
    private AuthorIncomeMapper authorIncomeMapper;

    @Mock
    private SettlementPayoutService payoutService;

    @InjectMocks
    private SettlementService settlementService;

    /** 聚合未结算稿酬（100 + 50）生成结算单，并批量回写 settlement_id 锁定流水 */
    @Test
    void createSettlement_aggregates_incomes_and_locks_them() {
        AuthorIncomeEntity i1 = new AuthorIncomeEntity();
        i1.setId(1L);
        i1.setAuthorId(10L);
        i1.setSettleMonth("2026-09");
        i1.setAmount(new BigDecimal("100.00"));
        AuthorIncomeEntity i2 = new AuthorIncomeEntity();
        i2.setId(2L);
        i2.setAuthorId(10L);
        i2.setSettleMonth("2026-09");
        i2.setAmount(new BigDecimal("50.00"));

        when(authorIncomeMapper.selectList(any())).thenReturn(List.of(i1, i2));
        // BaseMapper 有 insert(T) / insert(Collection<T>) 重载，需用带类型的 any() 消除歧义
        when(settlementOrderMapper.insert(any(SettlementOrderEntity.class))).thenReturn(1);

        SettlementOrderEntity order = settlementService.createSettlement(10L, "2026-09");

        assertNotNull(order.getId());
        assertEquals("2026-09", order.getPeriod());
        assertEquals(SettlementStateMachine.STATUS_PENDING, order.getStatus());
        assertEquals(0, new BigDecimal("150.00").compareTo(order.getTotalAmount()));

        // 批量回写锁定：一次 update(null, wrapper)，而非逐条 updateById
        verify(authorIncomeMapper).update(isNull(), ArgumentMatchers.<UpdateWrapper<AuthorIncomeEntity>>any());
        verify(authorIncomeMapper, never()).updateById(any(AuthorIncomeEntity.class));
    }

    /** 主链路：审核通过(0→1) 后打款成功(1→2)，并落渠道流水号 */
    @Test
    void audit_then_payout_happy_path() {
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.setId(1001L);
        order.setAuthorId(10L);
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setStatus(SettlementStateMachine.STATUS_PENDING);
        when(settlementOrderMapper.selectById(1001L)).thenReturn(order);

        SettlementOrderEntity audited = settlementService.auditApprove(1001L, 99L);
        assertEquals(SettlementStateMachine.STATUS_SETTLED, audited.getStatus());
        assertEquals(99L, audited.getOperatorId());

        when(payoutService.payout(eq(10L), any(BigDecimal.class)))
                .thenReturn(PayResult.success("DRY-1001-0001", "stub"));

        SettlementOrderEntity paid = settlementService.payout(1001L);
        assertEquals(SettlementStateMachine.STATUS_PAID, paid.getStatus());
        assertEquals("DRY-1001-0001", paid.getPaySerial());
    }

    /** 打款失败置 3，重试后回到 2（幂等键 pay_serial 不变） */
    @Test
    void payout_failure_then_retry_succeeds() {
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.setId(1001L);
        order.setAuthorId(10L);
        order.setTotalAmount(new BigDecimal("150.00"));
        order.setStatus(SettlementStateMachine.STATUS_SETTLED);
        when(settlementOrderMapper.selectById(1001L)).thenReturn(order);

        when(payoutService.payout(eq(10L), any(BigDecimal.class)))
                .thenReturn(PayResult.failed("stub"));
        SettlementOrderEntity failed = settlementService.payout(1001L);
        assertEquals(SettlementStateMachine.STATUS_FAILED, failed.getStatus());

        when(payoutService.payout(eq(10L), any(BigDecimal.class)))
                .thenReturn(PayResult.success("DRY-1001-0001", "stub"));
        SettlementOrderEntity retried = settlementService.retryPayout(1001L);
        assertEquals(SettlementStateMachine.STATUS_PAID, retried.getStatus());
        assertEquals("DRY-1001-0001", retried.getPaySerial());
    }

    /** 已打款再调打款：直接返回，不得再次出款 */
    @Test
    void payout_is_idempotent_when_already_paid() {
        SettlementOrderEntity paid = new SettlementOrderEntity();
        paid.setId(1001L);
        paid.setAuthorId(10L);
        paid.setStatus(SettlementStateMachine.STATUS_PAID);
        paid.setPaySerial("DRY-1001-0001");
        when(settlementOrderMapper.selectById(1001L)).thenReturn(paid);

        SettlementOrderEntity result = settlementService.payout(1001L);

        assertEquals(SettlementStateMachine.STATUS_PAID, result.getStatus());
        verify(payoutService, never()).payout(any(), any());
    }
}

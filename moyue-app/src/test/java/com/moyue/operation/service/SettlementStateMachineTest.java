package com.moyue.operation.service;

import com.moyue.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算单状态机纯单元单测（无 DB 依赖）。
 * 覆盖：0→1→2 主链路、非法迁移抛异常、失败(3)→重试(1)、已打款(2)为终态。
 */
class SettlementStateMachineTest {

    @Test
    void audit_transitions_pending_to_settled() {
        assertEquals(SettlementStateMachine.STATUS_SETTLED,
                SettlementStateMachine.audit(SettlementStateMachine.STATUS_PENDING));
    }

    @Test
    void audit_throws_when_not_pending() {
        assertThrows(BizException.class,
                () -> SettlementStateMachine.audit(SettlementStateMachine.STATUS_SETTLED));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.audit(SettlementStateMachine.STATUS_PAID));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.audit(SettlementStateMachine.STATUS_FAILED));
    }

    @Test
    void payout_precondition_only_settled() {
        // 合法：已结算待打款(1) 可打款，不抛异常
        SettlementStateMachine.assertCanPayout(SettlementStateMachine.STATUS_SETTLED);
        // 非法：其余状态均不可打款
        assertThrows(BizException.class,
                () -> SettlementStateMachine.assertCanPayout(SettlementStateMachine.STATUS_PENDING));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.assertCanPayout(SettlementStateMachine.STATUS_PAID));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.assertCanPayout(SettlementStateMachine.STATUS_FAILED));
    }

    @Test
    void retry_transitions_failed_to_settled() {
        assertEquals(SettlementStateMachine.STATUS_SETTLED,
                SettlementStateMachine.retry(SettlementStateMachine.STATUS_FAILED));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.retry(SettlementStateMachine.STATUS_PENDING));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.retry(SettlementStateMachine.STATUS_SETTLED));
        assertThrows(BizException.class,
                () -> SettlementStateMachine.retry(SettlementStateMachine.STATUS_PAID));
    }

    @Test
    void isPaid_only_for_paid_status() {
        assertTrue(SettlementStateMachine.isPaid(SettlementStateMachine.STATUS_PAID));
        assertFalse(SettlementStateMachine.isPaid(SettlementStateMachine.STATUS_PENDING));
        assertFalse(SettlementStateMachine.isPaid(SettlementStateMachine.STATUS_SETTLED));
        assertFalse(SettlementStateMachine.isPaid(SettlementStateMachine.STATUS_FAILED));
    }

    @Test
    void full_happy_path_zero_one_two() {
        int s = SettlementStateMachine.STATUS_PENDING;
        s = SettlementStateMachine.audit(s);
        assertEquals(SettlementStateMachine.STATUS_SETTLED, s);
        SettlementStateMachine.assertCanPayout(s);
        // 打款成功落终态（与 SettlementPayoutService 语义一致）
        s = SettlementStateMachine.STATUS_PAID;
        assertTrue(SettlementStateMachine.isPaid(s));
    }
}

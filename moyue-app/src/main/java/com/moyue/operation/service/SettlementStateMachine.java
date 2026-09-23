package com.moyue.operation.service;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;

/**
 * 结算单状态机（纯函数，无 DB 依赖，便于单元单测）。
 *
 * <pre>
 *   0 待结算 ──审核──▶ 1 已结算待打款 ──打款成功──▶ 2 已打款
 *                         │
 *                         └────打款失败────▶ 3 打款失败 ──重试──▶ 1 已结算待打款
 * </pre>
 *
 * 每个迁移方法在状态非法时抛 {@link BizException}，调用方据此返回统一业务错误。
 */
public final class SettlementStateMachine {

    /** 0 待结算 */
    public static final int STATUS_PENDING = 0;
    /** 1 已结算待打款 */
    public static final int STATUS_SETTLED = 1;
    /** 2 已打款（终态） */
    public static final int STATUS_PAID = 2;
    /** 3 打款失败（可重试） */
    public static final int STATUS_FAILED = 3;

    private SettlementStateMachine() {
    }

    /** 审核：待结算(0) → 已结算待打款(1) */
    public static int audit(int current) {
        if (current != STATUS_PENDING) {
            throw new BizException(ResultCode.PARAM_ERROR, "结算单状态非法，仅「待结算」可审核");
        }
        return STATUS_SETTLED;
    }

    /** 打款前置校验：仅「已结算待打款(1)」可发起打款，否则抛异常 */
    public static void assertCanPayout(int current) {
        if (current != STATUS_SETTLED) {
            throw new BizException(ResultCode.PARAM_ERROR, "结算单状态非法，仅「已结算待打款」可打款");
        }
    }

    /** 重试前置校验：仅「打款失败(3)」可重试，否则抛异常；重试后回到「已结算待打款(1)」 */
    public static int retry(int current) {
        if (current != STATUS_FAILED) {
            throw new BizException(ResultCode.PARAM_ERROR, "结算单状态非法，仅「打款失败」可重试");
        }
        return STATUS_SETTLED;
    }

    /** 是否已为终态「已打款」 */
    public static boolean isPaid(int current) {
        return current == STATUS_PAID;
    }
}

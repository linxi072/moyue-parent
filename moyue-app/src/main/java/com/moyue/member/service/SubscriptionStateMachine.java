package com.moyue.member.service;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;

/**
 * 订阅单状态机（纯函数，无 DB 依赖，便于单元单测）。
 *
 * <pre>
 *   0 待支付 ──支付成功──▶ 1 生效中 ──到期──▶ 2 已过期
 *                         │
 *                         └────取消──▶ 3 已取消
 * </pre>
 *
 * 每个迁移方法在状态非法时抛 {@link BizException}，调用方据此返回统一业务错误。
 */
public final class SubscriptionStateMachine {

    /** 0 待支付 */
    public static final int STATUS_PENDING = 0;
    /** 1 生效中 */
    public static final int STATUS_ACTIVE = 1;
    /** 2 已过期（终态） */
    public static final int STATUS_EXPIRED = 2;
    /** 3 已取消（终态） */
    public static final int STATUS_CANCELLED = 3;

    private SubscriptionStateMachine() {
    }

    /** 支付成功：待支付(0) → 生效中(1) */
    public static int activate(int current) {
        if (current != STATUS_PENDING) {
            throw new BizException(ResultCode.PARAM_ERROR, "订阅状态非法，仅「待支付」可激活");
        }
        return STATUS_ACTIVE;
    }

    /** 到期：生效中(1) → 已过期(2) */
    public static int expire(int current) {
        if (current != STATUS_ACTIVE) {
            throw new BizException(ResultCode.PARAM_ERROR, "订阅状态非法，仅「生效中」可到期");
        }
        return STATUS_EXPIRED;
    }

    /** 取消：待支付(0)/生效中(1) → 已取消(3) */
    public static int cancel(int current) {
        if (current != STATUS_PENDING && current != STATUS_ACTIVE) {
            throw new BizException(ResultCode.PARAM_ERROR, "订阅状态非法，仅「待支付/生效中」可取消");
        }
        return STATUS_CANCELLED;
    }

    /** 是否生效中 */
    public static boolean isActive(int current) {
        return current == STATUS_ACTIVE;
    }
}

package com.moyue.commerce.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 积分流水实体（每笔积分变动一条）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_points_log")
public class PointsLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务类型：1 充值 / 2 打赏 / 3 消费 / 4 退款 */
    public static final int BIZ_RECHARGE = 1;
    /** 打赏 */
    public static final int BIZ_REWARD = 2;
    /** 消费（含积分兑换） */
    public static final int BIZ_CONSUME = 3;
    /** 退款 */
    public static final int BIZ_REFUND = 4;
    /** 签到 */
    public static final int BIZ_SIGN = 5;

    /** 用户 ID */
    private Long userId;

    /** 业务类型 */
    private Integer bizType;

    /** 变动积分（正数增加 / 负数扣减） */
    private BigDecimal changeAmount;

    /** 变动后账户余额 */
    private BigDecimal balanceAfter;

    /** 关联业务单号 */
    private String refId;
}

package com.moyue.commerce.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 用户积分账户实体。
 *
 * <p>余额唯一来源：打赏 / 兑换 / 充值 / 退款均通过 {@code PointsService.changePoints}
 * 在该账户上原子增减，余额为负时直接拒绝。</p>
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_points_account")
public class PointsAccount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 可用积分 */
    private BigDecimal balance;

    /** 累计获得积分 */
    private BigDecimal totalIncome;

    /** 累计消费积分 */
    private BigDecimal totalConsume;

    /** 冻结积分 */
    private BigDecimal frozen;
}

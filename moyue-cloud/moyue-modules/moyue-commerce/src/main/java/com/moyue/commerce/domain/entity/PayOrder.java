package com.moyue.commerce.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 付费订单实体（书币 / 会员 / 打赏）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_pay_order")
public class PayOrder extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号（业务生成，唯一索引） */
    private String orderNo;

    /** 用户 ID */
    private Long userId;

    /** 商品名称 */
    private String productName;

    /** 商品类型：1 书币 / 2 会员 / 3 打赏 */
    private Integer productType;

    /** 金额（元） */
    private BigDecimal amount;

    /** 数量 */
    private Integer quantity;

    /** 支付渠道：1 微信 / 2 支付宝 / 3 余额 */
    private Integer payChannel;

    /** 状态：0 待付 / 1 已付 / 2 退款 / 3 关闭 */
    private Integer status;

    /** 第三方交易号 */
    private String tradeNo;
}

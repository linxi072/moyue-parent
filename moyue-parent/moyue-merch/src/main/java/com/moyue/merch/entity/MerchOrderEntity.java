package com.moyue.merch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周边订单实体，映射 merch_order 表。
 * 一次结算一个 order_no，按购物车行拆单（一行一商品）；
 * 支付按 order_no 整单幂等支付。status：0 待支付 / 1 已支付 / 2 已取消。
 */
@Data
@TableName("merch_order")
public class MerchOrderEntity {

    /** 订单主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号（一次结算一个，整单共用） */
    private String orderNo;

    /** 下单人 → user.id */
    private Long userId;

    /** 商品 ID → merch_product.id */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 购买数量 */
    private Integer quantity;

    /** 本行金额（元）= price * quantity */
    private BigDecimal totalAmount;

    /** 状态：0 待支付 / 1 已支付 / 2 已取消 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

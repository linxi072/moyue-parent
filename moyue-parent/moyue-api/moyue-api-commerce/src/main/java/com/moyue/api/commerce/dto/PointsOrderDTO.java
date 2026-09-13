package com.moyue.api.commerce.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 积分兑换订单数据传输对象（跨服务共享）。
 * 字段与 points_order 表一一对应（is_deleted 逻辑删除字段不在出参中暴露）。
 */
@Data
public class PointsOrderDTO implements Serializable {

    private Long id;

    /** 兑换人 → user.id */
    private Long userId;

    /** 商品 ID → points_product.id */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 兑换消耗积分 */
    private Integer costPoints;

    /** 状态：0 待兑换 / 1 已兑换 / 2 已取消 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

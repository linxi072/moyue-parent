package com.moyue.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分兑换订单实体，映射 points_order 表。
 * 主键为雪花 ID。
 */
@Data
@TableName("points_order")
public class PointsOrderEntity {

    /** 订单主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
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

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

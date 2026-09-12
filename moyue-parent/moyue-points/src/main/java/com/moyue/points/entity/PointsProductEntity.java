package com.moyue.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分商品实体，映射 points_product 表。
 * 主键为雪花 ID；下架商品仅改 status 字段，不物理删除行。
 */
@Data
@TableName("points_product")
public class PointsProductEntity {

    /** 商品主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品描述 */
    private String description;

    /** 商品图片 */
    private String imageUrl;

    /** 兑换所需积分 */
    private Integer costPoints;

    /** 库存 */
    private Integer stock;

    /** 状态：1 上架 / 2 下架 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

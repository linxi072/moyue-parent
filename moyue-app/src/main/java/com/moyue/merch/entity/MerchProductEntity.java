package com.moyue.merch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周边商品实体，映射 merch_product 表。
 * status：1 上架 / 2 下架。
 */
@Data
@TableName("merch_product")
public class MerchProductEntity {

    /** 商品主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品描述 */
    private String description;

    /** 商品图片 */
    private String imageUrl;

    /** 售价（元） */
    private BigDecimal price;

    /** 库存 */
    private Integer stock;

    /** 累计销量 */
    private Integer sales;

    /** 状态：1 上架 / 2 下架 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

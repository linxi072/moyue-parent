package com.moyue.merch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 周边购物车实体，映射 merch_cart 表。
 * uk_user_product 保证一人一商品一条：重复加入按数量累加，软删行复活。
 */
@Data
@TableName("merch_cart")
public class MerchCartEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID → user.id */
    private Long userId;

    /** 商品 ID → merch_product.id */
    private Long productId;

    /** 数量 */
    private Integer quantity;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 加入时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

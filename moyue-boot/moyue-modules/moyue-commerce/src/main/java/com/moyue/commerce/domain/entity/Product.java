package com.moyue.commerce.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 积分兑换商品实体（书币 / 会员等）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_product")
public class Product extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品名称 */
    private String name;

    /** 类型：1 书币 / 2 会员 */
    private Integer type;

    /** 售价（元） */
    private BigDecimal priceAmount;

    /** 兑换所需积分 */
    private Integer points;

    /** 库存 */
    private Integer stock;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;

    /** 排序（越大越靠前） */
    private Integer sort;
}

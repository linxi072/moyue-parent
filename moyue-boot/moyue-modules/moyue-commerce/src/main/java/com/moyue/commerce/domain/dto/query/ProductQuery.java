package com.moyue.commerce.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 兑换商品查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {

    /** 商品名称，模糊匹配 */
    private String name;

    /** 类型：1 书币 / 2 会员 */
    private Integer type;

    /** 状态：0 下架 / 1 上架 */
    private Integer status;
}

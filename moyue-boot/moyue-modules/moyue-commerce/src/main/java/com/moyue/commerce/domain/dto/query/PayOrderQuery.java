package com.moyue.commerce.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 付费订单查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayOrderQuery extends PageQuery {

    /** 订单号，模糊匹配 */
    private String orderNo;

    /** 用户 ID */
    private Long userId;

    /** 商品类型：1 书币 / 2 会员 / 3 打赏 */
    private Integer productType;

    /** 状态：0 待付 / 1 已付 / 2 退款 / 3 关闭 */
    private Integer status;
}

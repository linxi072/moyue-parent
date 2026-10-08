package com.moyue.commerce.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.commerce.domain.dto.query.PayOrderQuery;
import com.moyue.commerce.domain.entity.PayOrder;
import com.moyue.commerce.domain.vo.PayOrderVO;

import java.util.Map;

/**
 * 商业化域（付费订单）服务。
 *
 * @author moyue
 */
public interface PayOrderService {

    PageResult<PayOrderVO> pageOrders(PayOrderQuery query);

    Long createOrder(PayOrder entity);

    boolean updateOrder(PayOrder entity);

    boolean deleteOrder(Long orderId);

    /**
     * 退款：仅已付（status=1）订单可退，置为已退款（status=2）。
     *
     * <p>幂等：已是已退款状态直接返回 true。真实退款到第三方支付为结构占位（沙箱无凭证）。</p>
     *
     * @param orderId 订单 ID
     * @return 是否成功
     */
    boolean refundOrder(Long orderId);

    /** 订单概览：总数 / 已付 / 退款 / 已付总额 */
    Map<String, Object> summary();
}

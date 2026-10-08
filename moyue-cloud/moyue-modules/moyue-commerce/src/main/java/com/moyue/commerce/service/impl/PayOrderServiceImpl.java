package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.commerce.domain.dto.query.PayOrderQuery;
import com.moyue.commerce.domain.entity.PayOrder;
import com.moyue.commerce.domain.vo.PayOrderVO;
import com.moyue.commerce.mapper.PayOrderMapper;
import com.moyue.commerce.service.PayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商业化域（付费订单）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements PayOrderService {

    private final PayOrderMapper orderMapper;

    @Override
    public PageResult<PayOrderVO> pageOrders(PayOrderQuery query) {
        var page = PageUtils.<PayOrder>page(query);
        var result = orderMapper.selectPage(page, new LambdaQueryWrapper<PayOrder>()
                .like(StringUtils.hasText(query.getOrderNo()), PayOrder::getOrderNo, query.getOrderNo())
                .eq(query.getUserId() != null, PayOrder::getUserId, query.getUserId())
                .eq(query.getProductType() != null, PayOrder::getProductType, query.getProductType())
                .eq(query.getStatus() != null, PayOrder::getStatus, query.getStatus())
                .orderByDesc(PayOrder::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(PayOrder entity) {
        if (entity.getAmount() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单金额不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        if (entity.getQuantity() == null) {
            entity.setQuantity(1);
        }
        if (!StringUtils.hasText(entity.getOrderNo())) {
            entity.setOrderNo("PAY" + System.nanoTime());
        }
        orderMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrder(PayOrder entity) {
        PayOrder exist = orderMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("付费订单");
        }
        return orderMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOrder(Long orderId) {
        PayOrder exist = orderMapper.selectById(orderId);
        if (exist == null) {
            throw BusinessException.notFound("付费订单");
        }
        return orderMapper.deleteById(orderId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundOrder(Long orderId) {
        PayOrder exist = orderMapper.selectById(orderId);
        if (exist == null) {
            throw BusinessException.notFound("付费订单");
        }
        // 幂等：已退款直接返回
        if (Integer.valueOf(2).equals(exist.getStatus())) {
            return true;
        }
        if (exist.getStatus() == null || exist.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅已支付订单可退款");
        }
        PayOrder upd = new PayOrder();
        upd.setId(orderId);
        upd.setStatus(2);
        return orderMapper.updateById(upd) > 0;
    }

    @Override
    public Map<String, Object> summary() {
        List<PayOrder> all = orderMapper.selectList(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalOrders", (long) all.size());
        m.put("paidOrders", all.stream().filter(o -> o.getStatus() != null && o.getStatus() == 1).count());
        m.put("refundOrders", all.stream().filter(o -> o.getStatus() != null && o.getStatus() == 2).count());
        m.put("totalPaidAmount", all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 1 && o.getAmount() != null)
                .map(PayOrder::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }

    private PayOrderVO toVO(PayOrder e) {
        return PayOrderVO.builder()
                .id(e.getId())
                .orderNo(e.getOrderNo())
                .userId(e.getUserId())
                .productName(e.getProductName())
                .productType(e.getProductType())
                .amount(e.getAmount())
                .quantity(e.getQuantity())
                .payChannel(e.getPayChannel())
                .status(e.getStatus())
                .tradeNo(e.getTradeNo())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}

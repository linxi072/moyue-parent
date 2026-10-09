package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.commerce.domain.entity.PayOrder;
import com.moyue.commerce.mapper.PayOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 付费订单服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：退款状态机（仅已支付可退、已退幂等、不存在 404、非已支付拒退）与下单默认值（订单号/状态/数量）。
 */
@ExtendWith(MockitoExtension.class)
class PayOrderServiceImplTest {

    @Mock
    private PayOrderMapper orderMapper;

    @InjectMocks
    private PayOrderServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, PayOrder.class);
    }

    private static PayOrder order(Long id, Integer status) {
        PayOrder o = new PayOrder();
        o.setId(id);
        o.setStatus(status);
        return o;
    }

    @Test
    void refund_notFound_throwsNotFound() {
        when(orderMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refundOrder(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void refund_nonPaid_throwsParamError() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L, 0));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refundOrder(5L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    void refund_alreadyRefunded_isIdempotent() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L, 2));

        assertTrue(service.refundOrder(5L));
        verify(orderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    void refund_paid_marksRefunded() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L, 1));
        when(orderMapper.updateById(any(PayOrder.class))).thenReturn(1);

        assertTrue(service.refundOrder(5L));

        ArgumentCaptor<PayOrder> cap = ArgumentCaptor.forClass(PayOrder.class);
        verify(orderMapper).updateById(cap.capture());
        assertEquals(2, cap.getValue().getStatus());
    }

    @Test
    void createOrder_autoOrderNoAndDefaultStatus() {
        when(orderMapper.insert(any(PayOrder.class))).thenReturn(1);

        PayOrder e = new PayOrder();
        e.setAmount(new BigDecimal("9.90"));
        service.createOrder(e);

        ArgumentCaptor<PayOrder> cap = ArgumentCaptor.forClass(PayOrder.class);
        verify(orderMapper).insert(cap.capture());
        assertTrue(cap.getValue().getOrderNo().startsWith("PAY"));
        assertEquals(0, cap.getValue().getStatus());
        assertEquals(Integer.valueOf(1), cap.getValue().getQuantity());
    }
}

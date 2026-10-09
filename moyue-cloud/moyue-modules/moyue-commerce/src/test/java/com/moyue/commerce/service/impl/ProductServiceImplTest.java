package com.moyue.commerce.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.commerce.domain.entity.PayOrder;
import com.moyue.commerce.domain.entity.Product;
import com.moyue.commerce.mapper.PayOrderMapper;
import com.moyue.commerce.mapper.ProductMapper;
import com.moyue.commerce.service.PointsService;
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
 * 积分兑换商品服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：兑换参数校验、未上架/库存不足拒兑、兑换闭环（扣积分 → 原子减库存 → 落已付订单）。
 * 真实支付网关为结构占位，此处直接断言已生成 status=1 的积分渠道订单。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private PointsService pointsService;

    @Mock
    private PayOrderMapper orderMapper;

    @InjectMocks
    private ProductServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, Product.class);
    }

    private static Product product(Integer status, int points, int stock) {
        Product p = new Product();
        p.setId(1L);
        p.setName("会员月卡");
        p.setType(2);
        p.setPoints(points);
        p.setStock(stock);
        p.setStatus(status);
        p.setPriceAmount(new BigDecimal("20"));
        return p;
    }

    @Test
    void exchange_nullUser_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(1L, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void exchange_notFound_throwsNotFound() {
        when(productMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(1L, 9L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void exchange_offShelf_throwsParamError() {
        when(productMapper.selectById(1L)).thenReturn(product(0, 100, 10));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(1L, 9L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void exchange_insufficientStock_throwsParamError() {
        when(productMapper.selectById(1L)).thenReturn(product(1, 100, 5));
        when(pointsService.changePoints(anyLong(), anyInt(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(productMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exchange(1L, 9L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void exchange_success_deductsPointsStockAndCreatesOrder() {
        when(productMapper.selectById(1L)).thenReturn(product(1, 100, 5));
        when(pointsService.changePoints(anyLong(), anyInt(), any(), any(), any())).thenReturn(new BigDecimal("900"));
        when(productMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(orderMapper.insert(any(PayOrder.class))).thenReturn(1);

        service.exchange(1L, 9L);

        // 扣积分：金额为负
        ArgumentCaptor<BigDecimal> amt = ArgumentCaptor.forClass(BigDecimal.class);
        verify(pointsService).changePoints(eq(9L), anyInt(), amt.capture(), any(), any());
        assertTrue(amt.getValue().compareTo(BigDecimal.ZERO) < 0);
        // 原子减库存
        verify(productMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        // 落已付积分订单
        ArgumentCaptor<PayOrder> oc = ArgumentCaptor.forClass(PayOrder.class);
        verify(orderMapper).insert(oc.capture());
        assertEquals(1, oc.getValue().getStatus());
        assertTrue(oc.getValue().getOrderNo().startsWith("EX"));
    }
}

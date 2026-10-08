package com.moyue.ai.service.impl;

import com.moyue.ai.domain.entity.AiQuota;
import com.moyue.ai.mapper.AiQuotaMapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.ai.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 配额单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：getOrCreate 默认额度、deduct 不足抛 PAY_FAILED、deduct 成功扣减、reset 重置。
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceImplTest {

    @Mock
    private AiQuotaMapper quotaMapper;

    @InjectMocks
    private QuotaServiceImpl service;

    @Test
    void getOrCreate_creates_with_default_total() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(quotaMapper.insert(any(AiQuota.class))).thenReturn(1);

        AiQuota q = service.getOrCreate(7L);
        assertEquals(1000, q.getTotal());
        assertEquals(1000, q.getRemain());
    }

    @Test
    void getOrCreate_returns_existing() {
        AiQuota exist = new AiQuota();
        exist.setUserId(7L);
        exist.setTotal(100);
        exist.setUsed(10);
        exist.setRemain(90);
        when(quotaMapper.selectOne(any())).thenReturn(exist);

        AiQuota q = service.getOrCreate(7L);
        assertEquals(90, q.getRemain());
        verify(quotaMapper, never()).insert(any(AiQuota.class));
    }

    @Test
    void deduct_insufficient_throws_payFailed() {
        AiQuota q = new AiQuota();
        q.setUserId(7L);
        q.setRemain(5);
        when(quotaMapper.selectOne(any())).thenReturn(q);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deduct(7L, 10));
        assertEquals(ErrorCode.PAY_FAILED.getCode(), ex.getCode());
        verify(quotaMapper, never()).update(any(), any());
    }

    @Test
    void deduct_sufficient_updates() {
        AiQuota q = new AiQuota();
        q.setUserId(7L);
        q.setRemain(100);
        when(quotaMapper.selectOne(any())).thenReturn(q);
        when(quotaMapper.update(any(), any())).thenReturn(1);

        service.deduct(7L, 10);
        verify(quotaMapper).update(any(), any());
    }

    @Test
    void deduct_auto_creates_when_missing() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(quotaMapper.insert(any(AiQuota.class))).thenReturn(1);
        when(quotaMapper.update(any(), any())).thenReturn(1);

        service.deduct(7L, 10);
        verify(quotaMapper).insert(any(AiQuota.class));
        verify(quotaMapper).update(any(), any());
    }

    @Test
    void reset_inserts_with_total() {
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(quotaMapper.insert(any(AiQuota.class))).thenReturn(1);

        service.reset(7L, 500);
        ArgumentCaptor<AiQuota> cap = ArgumentCaptor.forClass(AiQuota.class);
        verify(quotaMapper).insert(cap.capture());
        assertEquals(500, cap.getValue().getTotal());
        assertEquals(500, cap.getValue().getRemain());
        assertEquals(0, cap.getValue().getUsed());
    }
}

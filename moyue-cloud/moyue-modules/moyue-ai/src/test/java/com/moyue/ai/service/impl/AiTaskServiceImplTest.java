package com.moyue.ai.service.impl;

import com.moyue.ai.domain.entity.AiTask;
import com.moyue.ai.mapper.AiTaskMapper;
import com.moyue.ai.service.AiTaskService;
import com.moyue.ai.service.QuotaService;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
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
 * AI 任务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：运行成功（扣配额 + 状态流转 + 结果非空）、配额不足拒绝、已执行不可重跑、任务不存在。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskServiceImplTest {

    @Mock
    private AiTaskMapper taskMapper;

    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private AiTaskServiceImpl service;

    private AiTask pendingTask() {
        AiTask t = new AiTask();
        t.setId(1L);
        t.setUserId(9L);
        t.setStatus(0);
        t.setPrompt("写一篇开篇");
        t.setTaskType(3);
        return t;
    }

    @Test
    void run_success_deducts_and_completes() {
        when(taskMapper.selectById(1L)).thenReturn(pendingTask());
        when(taskMapper.updateById(any(AiTask.class))).thenReturn(1);
        doNothing().when(quotaService).deduct(anyLong(), anyInt());

        assertTrue(service.run(1L));
        verify(quotaService).deduct(9L, 10);

        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).updateById(cap.capture());
        AiTask upd = cap.getValue();
        assertEquals(1, upd.getStatus());
        assertEquals(10, upd.getCostTokens());
        assertNotNull(upd.getResult());
        assertTrue(upd.getResult().contains("摘要"));
    }

    @Test
    void run_insufficient_quota_rejected() {
        when(taskMapper.selectById(1L)).thenReturn(pendingTask());
        doThrow(new BusinessException(ErrorCode.PAY_FAILED, "配额不足"))
                .when(quotaService).deduct(anyLong(), anyInt());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.run(1L));
        assertEquals(ErrorCode.PAY_FAILED.getCode(), ex.getCode());
        verify(taskMapper, never()).updateById(any(AiTask.class));
    }

    @Test
    void run_already_run_throws_paramError() {
        AiTask t = pendingTask();
        t.setStatus(1);
        when(taskMapper.selectById(1L)).thenReturn(t);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.run(1L));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(quotaService, never()).deduct(anyLong(), anyInt());
    }

    @Test
    void run_notFound_throws() {
        when(taskMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.run(1L));
    }
}

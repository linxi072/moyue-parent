package com.moyue.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.ai.domain.dto.query.AiTaskQuery;
import com.moyue.ai.domain.entity.AiTask;
import com.moyue.ai.domain.vo.AiTaskVO;
import com.moyue.ai.mapper.AiTaskMapper;
import com.moyue.ai.service.AiTaskService;
import com.moyue.ai.service.QuotaService;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

    /** 纯 Mockito 测试无 Spring 上下文，需手动注册实体 TableInfo 以支持 Lambda 包装器的列解析。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, AiTask.class);
    }

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

    // ---------------------------------------------------------------- C 端任务查询（G-L‴）

    @Test
    void pageMyTasks_forcesScopeToUser() {
        when(taskMapper.selectPage(any(), any())).thenReturn(new Page<AiTask>());
        AiTaskQuery query = new AiTaskQuery();
        query.setUserId(999L); // 调用方伪造他人 userId，应被覆盖

        service.pageMyTasks(42L, query);

        ArgumentCaptor<LambdaQueryWrapper<AiTask>> wcap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectPage(any(), wcap.capture());
        assertTrue(wcap.getValue().getCustomSqlSegment().contains("user_id"),
                "C 端任务列表必须按 userId 限定作用域");
    }

    @Test
    void getMyTask_returnsOwnedTask() {
        AiTask t = pendingTask();
        t.setId(5L);
        when(taskMapper.selectById(5L)).thenReturn(t);

        AiTaskVO vo = service.getMyTask(9L, 5L);

        assertEquals(5L, vo.getId());
        assertEquals(9L, vo.getUserId());
    }

    @Test
    void getMyTask_notOwned_throwsNotFound() {
        AiTask t = pendingTask();
        t.setId(5L);
        t.setUserId(99L); // 他人任务
        when(taskMapper.selectById(5L)).thenReturn(t);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMyTask(42L, 5L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getMyTask_notFound_throws() {
        when(taskMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMyTask(42L, 5L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}

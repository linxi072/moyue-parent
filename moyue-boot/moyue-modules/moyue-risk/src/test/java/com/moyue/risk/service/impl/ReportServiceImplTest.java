package com.moyue.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.risk.domain.entity.ReportTicket;
import com.moyue.risk.mapper.ReportTicketMapper;
import com.moyue.risk.service.ReportService;
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
 * 举报工单状态机单元测试（Mockito，无 Spring 上下文，免基础设施）。
 *
 * <p>覆盖：处理状态合法性校验、工单不存在、待处理→已处理流转、已处理重复处理拒绝。
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportTicketMapper reportMapper;

    @InjectMocks
    private ReportServiceImpl service;

    /** 纯 Mockito 测试无 Spring 上下文，需手动注册实体 TableInfo 以支持 LambdaUpdateWrapper 的列解析。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, ReportTicket.class);
    }


    @Test
    void handle_invalidStatus_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handle(1L, 9, "op", "x"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(reportMapper, never()).selectById(anyLong());
    }

    @Test
    void handle_notFound_throwsNotFound() {
        when(reportMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handle(1L, ReportTicket.STATUS_HANDLED, "op", "x"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void handle_pending_to_handled_updatesStatus() {
        ReportTicket t = new ReportTicket();
        t.setId(5L);
        t.setStatus(ReportTicket.STATUS_PENDING);
        when(reportMapper.selectById(5L)).thenReturn(t);
        when(reportMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertTrue(service.handle(5L, ReportTicket.STATUS_HANDLED, "op", "已处理"));

        ArgumentCaptor<LambdaUpdateWrapper<ReportTicket>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(reportMapper).update(isNull(), cap.capture());
    }

    @Test
    void handle_already_handled_throwsParamError() {
        ReportTicket t = new ReportTicket();
        t.setId(5L);
        t.setStatus(ReportTicket.STATUS_HANDLED);
        when(reportMapper.selectById(5L)).thenReturn(t);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handle(5L, ReportTicket.STATUS_REJECTED, "op", "x"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(reportMapper, never()).update(any(), any());
    }
}

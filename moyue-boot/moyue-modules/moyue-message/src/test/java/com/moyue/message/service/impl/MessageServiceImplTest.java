package com.moyue.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.message.domain.entity.Message;
import com.moyue.message.domain.entity.MessageTemplate;
import com.moyue.message.mapper.MessageMapper;
import com.moyue.message.mapper.MessageTemplateMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 站内信服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：标题校验、单发/群发落库（默认未读、系统身份）、模板渲染 ${name}、
 * 空接收人/空列表校验、未读计数、批量已读。
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageTemplateMapper templateMapper;

    @InjectMocks
    private MessageServiceImpl service;

    /** 纯 Mockito 测试无 Spring 上下文，需手动注册实体 TableInfo 以支持 Lambda 包装器的列解析。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, Message.class);
        TableInfoHelper.initTableInfo(assistant, MessageTemplate.class);
    }

    @Test
    void createMessage_blankTitle_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createMessage(new Message()));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void sendOne_nullToUser_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendOne(null, "t", "c", 1, null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void sendOne_withoutTemplate_insertsUnreadMessage() {
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        service.sendOne(7L, "标题", "正文", 2, null, null);

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(cap.capture());
        Message m = cap.getValue();
        assertEquals(7L, m.getToUser());
        assertEquals(0L, m.getFromUser());
        assertEquals(0, m.getReadFlag());
        assertEquals("标题", m.getTitle());
    }

    @Test
    void sendOne_withTemplate_rendersName() {
        MessageTemplate t = new MessageTemplate();
        t.setCode("WELCOME");
        t.setTitle("欢迎");
        t.setContent("您好 ${name}，欢迎回来");
        when(templateMapper.selectOne(any())).thenReturn(t);
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        service.sendOne(7L, "标题", "正文", 1, "WELCOME", "小明");

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(cap.capture());
        assertEquals("您好 小明，欢迎回来", cap.getValue().getContent());
        assertEquals("欢迎", cap.getValue().getTitle());
    }

    @Test
    void sendBatch_empty_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendBatch(List.of(), "t", "c", 1, null, null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void sendBatch_sendsOnePerUser() {
        when(messageMapper.insert(any(Message.class))).thenReturn(1);

        List<Long> ids = service.sendBatch(List.of(1L, 2L, 3L), "t", "c", 1, null, null);

        assertEquals(3, ids.size());
        verify(messageMapper, times(3)).insert(any(Message.class));
    }

    @Test
    void unreadCount_nullUser_returnsZero() {
        assertEquals(0L, service.unreadCount(null));
        verify(messageMapper, never()).selectCount(any());
    }

    @Test
    void unreadCount_countsUnread() {
        when(messageMapper.selectCount(any())).thenReturn(2L);
        assertEquals(2L, service.unreadCount(5L));
    }

    @Test
    void readAll_empty_returnsTrueWithoutUpdate() {
        assertTrue(service.readAll(List.of()));
        verify(messageMapper, never()).update(any(), any());
    }

    @Test
    void readAll_marksRead() {
        when(messageMapper.update(any(Message.class), any(LambdaQueryWrapper.class))).thenReturn(1);

        assertTrue(service.readAll(List.of(10L, 11L)));

        ArgumentCaptor<Message> cap = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).update(cap.capture(), any());
        assertEquals(1, cap.getValue().getReadFlag());
    }
}

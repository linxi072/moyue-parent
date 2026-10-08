package com.moyue.social.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.social.domain.entity.ImConversation;
import com.moyue.social.domain.entity.ImMessage;
import com.moyue.social.mapper.ImConversationMapper;
import com.moyue.social.mapper.ImMemberMapper;
import com.moyue.social.mapper.ImMessageMapper;
import com.moyue.social.service.ImService;
import com.moyue.social.ws.ImSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IM 会话管理运营端单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：禁用/启用会话、禁用值校验、会话不存在校验、运营查看消息。
 */
@ExtendWith(MockitoExtension.class)
class ImServiceImplTest {

    @Mock
    private ImConversationMapper conversationMapper;

    @Mock
    private ImMemberMapper memberMapper;

    @Mock
    private ImMessageMapper messageMapper;

    @Mock
    private ImSessionRegistry registry;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ImServiceImpl service;

    @Test
    void disableConversation_sets_flag() {
        ImConversation conv = new ImConversation();
        conv.setId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);
        when(conversationMapper.updateById(any(ImConversation.class))).thenReturn(1);

        assertTrue(service.disableConversation(1L, 1));
        ArgumentCaptor<ImConversation> cap = ArgumentCaptor.forClass(ImConversation.class);
        verify(conversationMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getDisabled());
    }

    @Test
    void disableConversation_invalid_value_throws() {
        ImConversation conv = new ImConversation();
        conv.setId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.disableConversation(1L, 5));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(conversationMapper, never()).updateById(any(ImConversation.class));
    }

    @Test
    void disableConversation_notFound_throws() {
        when(conversationMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.disableConversation(1L, 1));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void adminListMessages_returns_conversation_messages() {
        ImConversation conv = new ImConversation();
        conv.setId(1L);
        when(conversationMapper.selectById(1L)).thenReturn(conv);

        ImMessage m1 = new ImMessage();
        m1.setId(10L);
        m1.setContent("hi");
        ImMessage m2 = new ImMessage();
        m2.setId(11L);
        m2.setContent("hello");
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(m1, m2)));

        var list = service.adminListMessages(1L, 20);
        assertEquals(2, list.size());
    }

    @Test
    void adminListMessages_notFound_throws() {
        when(conversationMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.adminListMessages(1L, 20));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}

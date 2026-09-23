package com.moyue.ai;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.ai.engine.AiReplyEngine;
import com.moyue.ai.engine.ReplyContext;
import com.moyue.ai.engine.ReplyResult;
import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.entity.AiSessionEntity;
import com.moyue.ai.mapper.AiMessageMapper;
import com.moyue.ai.mapper.AiSessionMapper;
import com.moyue.ai.service.AiService;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.search.dto.QaIndexDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiService.chat 问答索引同步 hook 单测（Mockito 隔离 Mapper / 回复引擎 / Feign 客户端）：
 * 对话落库后异步推送一轮问答（question + answer，messageId=助手回复 ID）；
 * 客户端未注册 / 推送失败均不阻断主流程（异常吞在异步体内）。
 */
class AiServiceChatIndexHookTest {

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final AiReplyEngine replyEngine = mock(AiReplyEngine.class);
    private final SearchIndexClient searchIndexClient = mock(SearchIndexClient.class);

    private AiService createService(boolean withClient) {
        AiService s = new AiService();
        ReflectionTestUtils.setField(s, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(s, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(s, "replyEngine", replyEngine);
        if (withClient) {
            ReflectionTestUtils.setField(s, "searchIndexClient", searchIndexClient);
        }
        return s;
    }

    private void stubInserts() {
        doAnswer(inv -> {
            AiSessionEntity s = inv.getArgument(0);
            s.setId(100L);
            return 1;
        }).when(sessionMapper).insert(any(AiSessionEntity.class));
        doAnswer(inv -> {
            AiMessageEntity m = inv.getArgument(0);
            m.setId(m.getRole() == 2 ? 300L : 200L);
            return 1;
        }).when(messageMapper).insert(any(AiMessageEntity.class));
        when(replyEngine.reply(any(ReplyContext.class))).thenReturn(new ReplyResult("这是回复", true, "mock"));
    }

    @Test
    @DisplayName("chat 成功后异步推送索引：question=提问，answer=回复，messageId=助手消息 ID")
    void chatShouldPushQaIndexAsync() {
        AiService service = createService(true);
        stubInserts();

        AiMessageEntity answer = service.chat(1L, null, "怎么充值");

        assertThat(answer.getId()).isEqualTo(300L);
        ArgumentCaptor<QaIndexDTO> captor = ArgumentCaptor.forClass(QaIndexDTO.class);
        verify(searchIndexClient, timeout(2000)).indexQa(captor.capture());
        QaIndexDTO dto = captor.getValue();
        assertThat(dto.getMessageId()).isEqualTo(300L);
        assertThat(dto.getSessionId()).isEqualTo(100L);
        assertThat(dto.getQuestion()).isEqualTo("怎么充值");
        assertThat(dto.getAnswer()).isEqualTo("这是回复");
        assertThat(dto.getCreateTime()).isEqualTo(answer.getCreateTime());
    }

    @Test
    @DisplayName("searchIndexClient 未注册：chat 正常返回，不抛 NPE")
    void chatShouldWorkWithoutSearchClient() {
        AiService service = createService(false);
        stubInserts();

        AiMessageEntity answer = service.chat(1L, null, "你好");

        assertThat(answer.getContent()).isEqualTo("这是回复");
        verify(searchIndexClient, never()).indexQa(any());
    }

    @Test
    @DisplayName("推送失败（客户端抛异常）：异常吞在异步体内，chat 主流程不受影响")
    void chatShouldNotBlockWhenIndexPushFails() {
        AiService service = createService(true);
        stubInserts();
        doThrow(new RuntimeException("connection refused"))
                .when(searchIndexClient).indexQa(any());

        // 主流程必须正常返回（异步异常不允许外抛到调用线程）
        AiMessageEntity answer = service.chat(1L, null, "你好");

        assertThat(answer.getContent()).isEqualTo("这是回复");
        verify(searchIndexClient, timeout(2000)).indexQa(any());
    }

    @Test
    @DisplayName("消息内容为空：参数校验前置拦截，不触达回复引擎与索引推送")
    void chatShouldRejectBlankContent() {
        AiService service = createService(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.chat(1L, null, "  "))
                .isInstanceOf(com.moyue.common.BizException.class);
        verify(searchIndexClient, never()).indexQa(any());
    }
}

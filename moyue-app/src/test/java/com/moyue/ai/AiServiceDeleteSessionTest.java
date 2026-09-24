package com.moyue.ai;

import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.entity.AiSessionEntity;
import com.moyue.ai.mapper.AiMessageMapper;
import com.moyue.ai.mapper.AiSessionMapper;
import com.moyue.ai.service.AiService;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiService 会话删除单测（Mockito 隔离 Mapper / 检索客户端）：
 * 覆盖「级联逻辑删除 + ES 索引清理」「越权 FORBIDDEN」「不存在 RESOURCE_NOT_FOUND」
 * 「search 未注册不阻断」「清空全部返回删除数」五条核心契约。
 * 写法与 {@code AiServiceChatIndexHookTest} 对齐（ReflectionTestUtils 注入 mock，不启 Spring）。
 */
class AiServiceDeleteSessionTest {

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final SearchIndexClient searchIndexClient = mock(SearchIndexClient.class);

    private AiService createService(boolean withClient) {
        AiService s = new AiService();
        ReflectionTestUtils.setField(s, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(s, "messageMapper", messageMapper);
        if (withClient) {
            ReflectionTestUtils.setField(s, "searchIndexClient", searchIndexClient);
        }
        return s;
    }

    private AiSessionEntity session(Long id, Long userId) {
        AiSessionEntity s = new AiSessionEntity();
        s.setId(id);
        s.setUserId(userId);
        return s;
    }

    @Test
    @DisplayName("deleteSession 成功：逻辑删除会话 + 级联消息 + 异步清理 ES 索引")
    void deleteSession_success_cascadesAndCleansIndex() {
        AiService service = createService(true);
        when(sessionMapper.selectOne(any())).thenReturn(session(5001L, 7001L));
        when(sessionMapper.updateById(any(AiSessionEntity.class))).thenReturn(1);
        when(messageMapper.update(any(), any())).thenReturn(1);

        service.deleteSession(7001L, 5001L);

        ArgumentCaptor<AiSessionEntity> cap = ArgumentCaptor.forClass(AiSessionEntity.class);
        verify(sessionMapper).updateById(cap.capture());
        assertThat(cap.getValue().getIsDeleted()).isEqualTo(1);
        verify(messageMapper).update(any(), any());
        verify(searchIndexClient, timeout(2000)).removeQaBySession(5001L);
    }

    @Test
    @DisplayName("deleteSession 非本人会话：抛 FORBIDDEN，不触达删除与 ES 清理")
    void deleteSession_forbidden_whenNotOwner() {
        AiService service = createService(true);
        when(sessionMapper.selectOne(any())).thenReturn(session(5001L, 9999L));

        assertThatThrownBy(() -> service.deleteSession(7001L, 5001L))
                .isInstanceOf(BizException.class);
        verify(sessionMapper, never()).updateById(any(AiSessionEntity.class));
        verify(searchIndexClient, never()).removeQaBySession(any());
    }

    @Test
    @DisplayName("deleteSession 会话不存在：抛 RESOURCE_NOT_FOUND")
    void deleteSession_notFound() {
        AiService service = createService(true);
        when(sessionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.deleteSession(7001L, 5001L))
                .isInstanceOf(BizException.class);
        verify(sessionMapper, never()).updateById(any(AiSessionEntity.class));
    }

    @Test
    @DisplayName("deleteSession searchIndexClient 未注册：删除仍成功，不抛 NPE")
    void deleteSession_searchClientNull_safe() {
        AiService service = createService(false);
        when(sessionMapper.selectOne(any())).thenReturn(session(5001L, 7001L));
        when(sessionMapper.updateById(any(AiSessionEntity.class))).thenReturn(1);
        when(messageMapper.update(any(), any())).thenReturn(1);

        service.deleteSession(7001L, 5001L);

        verify(sessionMapper).updateById(any(AiSessionEntity.class));
        verify(searchIndexClient, never()).removeQaBySession(any());
    }

    @Test
    @DisplayName("clearSessions 成功：逐会话级联删除 + 清理 ES，返回删除会话数")
    void clearSessions_success() {
        AiService service = createService(true);
        when(sessionMapper.selectList(any())).thenReturn(List.of(session(5001L, 7001L), session(5002L, 7001L)));
        when(sessionMapper.updateById(any(AiSessionEntity.class))).thenReturn(1);
        when(messageMapper.update(any(), any())).thenReturn(1);

        long n = service.clearSessions(7001L);

        assertThat(n).isEqualTo(2);
        verify(searchIndexClient, timeout(2000)).removeQaBySession(5001L);
        verify(searchIndexClient, timeout(2000)).removeQaBySession(5002L);
    }
}

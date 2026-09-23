package com.moyue.bookshelf.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookshelfWebSocketHandler 单元测试（纯 Mockito，不加载 Spring 上下文）。
 * 校验连接登记 / 单用户推送 / 清理 / 在线计数。
 */
class BookshelfWebSocketHandlerTest {

    private BookshelfWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BookshelfWebSocketHandler();
    }

    private WebSocketSession mockSession(Long userId, boolean open) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getUri()).thenReturn(new URI("ws://localhost/ws/bookshelf?userId=" + userId));
        when(s.getId()).thenReturn("sess-" + userId);
        when(s.isOpen()).thenReturn(open);
        return s;
    }

    @Test
    void afterConnectionEstablished_registersSessionAndCounts() throws Exception {
        WebSocketSession s = mockSession(7001L, true);
        handler.afterConnectionEstablished(s);
        assertThat(handler.onlineUserCount()).isEqualTo(1);
        assertThat(handler.sessionCountOf(7001L)).isEqualTo(1);
    }

    @Test
    void afterConnectionEstablished_missingUserId_rejects() throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getUri()).thenReturn(new URI("ws://localhost/ws/bookshelf"));
        when(s.getId()).thenReturn("sess-x");
        handler.afterConnectionEstablished(s);
        verify(s).close(any(CloseStatus.class));
        assertThat(handler.onlineUserCount()).isZero();
    }

    @Test
    void pushEvent_sendsToRegisteredSession() throws Exception {
        WebSocketSession s = mockSession(7001L, true);
        handler.afterConnectionEstablished(s);

        BookshelfPushMessage msg = new BookshelfPushMessage();
        msg.setUserId(7001L);
        msg.setBookId(3001L);
        msg.setAction("ADD");
        handler.pushEvent(7001L, msg);

        verify(s).sendMessage(any(TextMessage.class));
    }

    @Test
    void pushEvent_noSession_noSend() throws Exception {
        WebSocketSession s = mockSession(7002L, true);
        handler.afterConnectionEstablished(s);
        handler.pushEvent(9999L, "{}");
        verify(s, never()).sendMessage(any());
    }

    @Test
    void afterConnectionClosed_cleansUp() throws Exception {
        WebSocketSession s = mockSession(7001L, true);
        handler.afterConnectionEstablished(s);
        handler.afterConnectionClosed(s, CloseStatus.NORMAL);
        assertThat(handler.sessionCountOf(7001L)).isZero();
        assertThat(handler.onlineUserCount()).isZero();
    }
}

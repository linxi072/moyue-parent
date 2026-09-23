package com.moyue.bookshelf.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 书架实时同步 WebSocket 处理器（P2-D）。
 * 复刻 {@code ImWebSocketHandler} 的 {@code Map<Long, Set<WebSocketSession>>} 会话登记模式，
 * 新增 {@link #pushEvent(Long, Object)} 仅向该 userId 的在线端广播（best-effort）。
 * 连接建立时从 query 参数 {@code userId} 解析用户标识并按 userId 分组登记。
 * 端点：{@code ws://{host}:8083/ws/bookshelf?userId={userId}}。
 */
@Component
public class BookshelfWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BookshelfWebSocketHandler.class);

    /** 连接登记用的 query 参数名 */
    private static final String USER_ID_PARAM = "userId";

    /** userId -> 该用户当前所有在线连接 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 广播专用序列化器：注册 JSR310 模块并关闭 WRITE_DATES_AS_TIMESTAMPS（与 ImWebSocketHandler 一致，
     * 固定实例使行为确定，避免注入未注册 JSR310 的自定义 ObjectMapper 导致序列化静默失效）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = parseUserId(session);
        if (userId == null) {
            log.warn("[bookshelf-ws] 连接缺少或非法的 userId 参数，拒绝注册：uri={}", session.getUri());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("[bookshelf-ws] 连接建立：userId={}, sessionId={}, 当前在线用户数={}",
                userId, session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        unregister(session);
        log.info("[bookshelf-ws] 连接关闭：sessionId={}, status={}, 当前在线用户数={}",
                session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        unregister(session);
        log.warn("[bookshelf-ws] 传输异常：sessionId={}", session.getId(), exception);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 向指定用户的所有在线端广播（best-effort）。
     * 目标 userId 为 null 或无可序列化内容 / 无在线会话时直接返回（no-op）。
     *
     * @param userId  目标用户 ID
     * @param payload 待推送对象，序列化为 JSON 文本帧
     */
    public void pushEvent(Long userId, Object payload) {
        if (userId == null || payload == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            // 目标用户无在线端：no-op（WsNotifyController 已据此返回 R.ok()）
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[bookshelf-ws] 消息序列化失败，放弃本次推送：payloadType={}",
                    payload.getClass().getName(), e);
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : new ArrayList<>(userSessions)) {
            sendSafely(session, message);
        }
    }

    /** 当前在线用户数（用于排查与监控） */
    public int onlineUserCount() {
        return sessions.size();
    }

    /** 指定用户的在线连接数 */
    public int sessionCountOf(Long userId) {
        if (userId == null) {
            return 0;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions == null ? 0 : userSessions.size();
    }

    /** 单个连接发送：异常仅记日志并摘除该连接 */
    private void sendSafely(WebSocketSession session, TextMessage message) {
        try {
            synchronized (session) {
                if (!session.isOpen()) {
                    unregister(session);
                    return;
                }
                session.sendMessage(message);
            }
        } catch (IOException e) {
            unregister(session);
            log.warn("[bookshelf-ws] 推送失败，已摘除连接：sessionId={}", session.getId(), e);
        } catch (Exception e) {
            log.warn("[bookshelf-ws] 推送异常：sessionId={}", session.getId(), e);
        }
    }

    /** 从登记表中移除连接；用户连接全部断开时一并移除该用户键 */
    private void unregister(WebSocketSession session) {
        Long userId = parseUserId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) {
            return;
        }
        userSessions.remove(session);
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
        }
    }

    /** 从连接 URI 的 query 中解析 userId，解析失败返回 null */
    private Long parseUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(USER_ID_PARAM);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

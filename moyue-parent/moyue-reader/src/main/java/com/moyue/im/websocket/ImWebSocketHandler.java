package com.moyue.im.websocket;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IM 消息实时推送处理器。
 * 会话建立时从 query 参数 {@code userId} 解析用户标识并按 userId 分组登记连接；
 * 业务侧调用 {@link #broadcast(List, Object)} 向指定用户集合推送消息。
 * 所有推送均为「尽力而为」：单个连接异常只影响该连接，绝不向上抛出。
 */
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketHandler.class);

    /** 连接登记用的 query 参数名：ws://host:8083/ws/im?userId=1 */
    private static final String USER_ID_PARAM = "userId";

    /** userId -> 该用户当前所有在线连接 */
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * 广播专用序列化器：注册 JSR310 模块并关闭 WRITE_DATES_AS_TIMESTAMPS，
     * 保证 MessageDTO.createTime（LocalDateTime）可被正确序列化。
     * 刻意不复用 Spring 容器中的 ObjectMapper：若将来有人注入一个未注册 JSR310 的自定义实例，
     * 序列化异常会在广播路径中被吞掉，导致广播静默失效且极难排查；固定使用本实例可让行为确定。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** 连接建立：解析 userId 并登记，缺少 userId 的连接直接拒绝 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = parseUserId(session);
        if (userId == null) {
            log.warn("[im-ws] 连接缺少或非法的 userId 参数，拒绝注册：uri={}", session.getUri());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("[im-ws] 连接建立：userId={}, sessionId={}, 当前在线用户数={}",
                userId, session.getId(), sessions.size());
    }

    /** 连接关闭：清理登记 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        unregister(session);
        log.info("[im-ws] 连接关闭：sessionId={}, status={}, 当前在线用户数={}",
                session.getId(), status, sessions.size());
    }

    /** 传输异常：同样清理登记，避免残留死连接 */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        unregister(session);
        log.warn("[im-ws] 传输异常：sessionId={}", session.getId(), exception);
    }

    /** 不支持分片消息 */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 向指定用户集合广播消息（尽力而为）。
     * 序列化失败或单个连接发送失败只记日志，不抛出异常，调用方无需感知。
     *
     * @param userIds 目标用户 ID 列表，为 null 或空时直接返回
     * @param payload 待推送对象，序列化为 JSON 文本帧
     */
    public void broadcast(List<Long> userIds, Object payload) {
        if (userIds == null || userIds.isEmpty() || payload == null) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[im-ws] 消息序列化失败，放弃本次广播：payloadType={}",
                    payload.getClass().getName(), e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            Set<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions == null || userSessions.isEmpty()) {
                continue;
            }
            // 复制一份快照，避免遍历过程中连接被移除导致并发问题
            for (WebSocketSession session : new ArrayList<>(userSessions)) {
                sendSafely(session, message);
            }
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
            // WebSocketSession 非线程安全，同一连接并发发送需串行化
            synchronized (session) {
                if (!session.isOpen()) {
                    unregister(session);
                    return;
                }
                session.sendMessage(message);
            }
        } catch (IOException e) {
            unregister(session);
            log.warn("[im-ws] 推送失败，已摘除连接：sessionId={}", session.getId(), e);
        } catch (Exception e) {
            log.warn("[im-ws] 推送异常：sessionId={}", session.getId(), e);
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

    /** 只读视图：当前登记的用户 ID（便于排查） */
    public Set<Long> onlineUserIds() {
        return Collections.unmodifiableSet(sessions.keySet());
    }
}

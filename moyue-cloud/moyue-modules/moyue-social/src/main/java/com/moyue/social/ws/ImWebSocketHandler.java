package com.moyue.social.ws;

import com.moyue.common.core.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * IM WebSocket 处理器。
 *
 * <p>连接即订阅：从查询参数 {@code ?uid=} 取用户 ID 注册到 {@link ImSessionRegistry}。
 * 客户端通过 REST 发消息，服务端在持久化后调用注册表定向广播，本处理器只负责连接生命周期。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    private final ImSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = parseUid(session);
        if (uid == null) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }
        session.getAttributes().put("uid", uid);
        registry.register(uid, session);
        log.info("IM WS 连接建立 uid={} online={}", uid, registry.onlineCount(uid));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = (Long) session.getAttributes().get("uid");
        if (uid != null) {
            registry.unregister(session);
            log.info("IM WS 连接关闭 uid={} status={}", uid, status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端上行消息统一走 REST 发送，这里仅回应心跳
        try {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        } catch (Exception ignored) {
        }
    }

    /**
     * 解析连接归属用户。
     *
     * <p><b>优先取身份头</b>：WebSocket 握手本质是一次 HTTP 请求，网关 {@code JwtAuthGlobalFilter}
     * 完成鉴权后会注入 {@code X-User-Id}，单体模式下 {@code BootAuthFilter} 同样会注入。
     * 以头部为准可以杜绝「改一个 ?uid= 就冒用他人会话」的越权问题。
     *
     * <p>仅当身份头缺失（如无网关的本地裸连调试）时才回退查询参数 {@code ?uid=}。
     */
    private Long parseUid(WebSocketSession session) {
        String header = session.getHandshakeHeaders().getFirst(Constants.HEADER_USER_ID);
        if (StringUtils.hasText(header)) {
            try {
                return Long.parseLong(header.trim());
            } catch (NumberFormatException ignored) {
                // 头部异常时继续尝试查询参数
            }
        }
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String kv : query.split("&")) {
            if (kv.startsWith("uid=")) {
                try {
                    return Long.parseLong(kv.substring(4));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}

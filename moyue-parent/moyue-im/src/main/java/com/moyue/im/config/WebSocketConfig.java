package com.moyue.im.config;

import com.moyue.im.websocket.ImWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 IM 消息推送端点。
 * 仅使用原生 spring-websocket（{@code WebSocketConfigurer}），
 * 不与 {@code @ServerEndpoint} 方式混用，避免两套容器实现冲突。
 * 端点：{@code ws://{host}:8093/ws/im?userId={userId}}
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;

    public WebSocketConfig(ImWebSocketHandler imWebSocketHandler) {
        this.imWebSocketHandler = imWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 允许跨域：本地演示环境（Vite 5173 等）可直接连 IM 服务
        registry.addHandler(imWebSocketHandler, "/ws/im").setAllowedOrigins("*");
    }
}

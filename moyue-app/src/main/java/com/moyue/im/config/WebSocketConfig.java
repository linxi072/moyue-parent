package com.moyue.im.config;

import com.moyue.bookshelf.websocket.BookshelfWebSocketHandler;
import com.moyue.im.websocket.ImWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册 IM 消息推送与书架实时同步端点。
 * 仅使用原生 spring-websocket（{@code WebSocketConfigurer}），不与 {@code @ServerEndpoint} 方式混用。
 * 端点：{@code ws://{host}:8083/ws/im?userId={userId}}、{@code ws://{host}:8083/ws/bookshelf?userId={userId}}。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;
    private final BookshelfWebSocketHandler bookshelfWebSocketHandler;

    public WebSocketConfig(ImWebSocketHandler imWebSocketHandler,
                           BookshelfWebSocketHandler bookshelfWebSocketHandler) {
        this.imWebSocketHandler = imWebSocketHandler;
        this.bookshelfWebSocketHandler = bookshelfWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 允许跨域：本地演示环境（Vite 5173 等）可直接连社区域服务
        registry.addHandler(imWebSocketHandler, "/ws/im").setAllowedOrigins("*");
        // P2-D 书架实时同步端点
        registry.addHandler(bookshelfWebSocketHandler, "/ws/bookshelf").setAllowedOrigins("*");
    }
}

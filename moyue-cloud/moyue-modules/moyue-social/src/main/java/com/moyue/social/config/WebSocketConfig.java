package com.moyue.social.config;

import com.moyue.social.ws.ImWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * IM WebSocket 配置：注册 {@code /ws/im} 端点。
 *
 * <p>网关已将该路径路由到 social 服务（见 moyue-gateway 路由表），跨域放开由
 * setAllowedOriginPatterns 控制（生产应收窄到自有域名）。
 *
 * @author moyue
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .setAllowedOriginPatterns("*");
    }
}

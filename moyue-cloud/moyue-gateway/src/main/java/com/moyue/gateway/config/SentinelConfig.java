package com.moyue.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.R;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Sentinel 限流熔断配置（架构说明书 4.1：网关侧限流熔断）。
 *
 * <p>限流返回统一错误码 {@code 30001 TOO_MANY_REQUESTS}，前端据此提示「请求过于频繁」，
 * 与业务异常走同一套错误码表。
 *
 * @author moyue
 */
@Configuration
public class SentinelConfig {

    @Bean
    @Order(-200)
    public SentinelGatewayFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean
    public BlockRequestHandler blockRequestHandler(ObjectMapper objectMapper) {
        GatewayCallbackManager.setBlockHandler(new BlockRequestHandler() {
            @Override
            public Mono<ServerResponse> handleRequest(org.springframework.web.server.ServerWebExchange exchange,
                                                      Throwable ex) {
                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(R.fail(ErrorCode.TOO_MANY_REQUESTS.getCode(), "请求过于频繁，请稍后再试"));
            }
        });
        return GatewayCallbackManager.getBlockHandler();
    }
}

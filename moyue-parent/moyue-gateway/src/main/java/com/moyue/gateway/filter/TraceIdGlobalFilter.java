package com.moyue.gateway.filter;

import com.moyue.common.Constants;
import com.moyue.common.obs.TraceIdGenerator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关侧 TraceId 入口（Order 高于 JwtAuthGlobalFilter 的 -1）：
 * 请求进入时生成/透传 X-Trace-Id，注入到下游请求头，使全链路（网关→服务→Feign）共享同一追踪号；
 * 同时回写响应头便于联调。服务端 MDC 由各服务的 TraceIdFilter 负责（WebFlux 此处不写 MDC）。
 */
@Component
@Order(-2)
public class TraceIdGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(Constants.TRACE_ID_HEADER);
        final String traceId = (incoming == null || incoming.isBlank())
                ? TraceIdGenerator.generate()
                : incoming;

        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header(Constants.TRACE_ID_HEADER, traceId)
                        .build())
                .build();
        mutated.getResponse().getHeaders().add(Constants.TRACE_ID_HEADER, traceId);
        return chain.filter(mutated);
    }
}

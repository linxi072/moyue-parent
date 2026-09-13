package com.moyue.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sentinel 网关统一降级处理器（P2-17）。
 *
 * <p>被拦截的请求不再抛默认异常，而是返回统一响应体 {@code R<T>}，HTTP 状态恒为 200，
 * 由业务码区分原因（架构设计 §七-12「Sentinel 降级体」）：</p>
 * <ul>
 *   <li>熔断降级（{@link DegradeException}）→ {@code SERVICE_DEGRADED(40002)}；</li>
 *   <li>其余 {@code BlockException}（限流等）→ {@code FREQUENCY_LIMIT(30001)}。</li>
 * </ul>
 */
public class GatewayBlockHandler implements BlockRequestHandler {

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable t) {
        ResultCode code = (t instanceof DegradeException)
                ? ResultCode.SERVICE_DEGRADED
                : ResultCode.FREQUENCY_LIMIT;
        R<Void> body = R.fail(code);
        return ServerResponse.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }
}

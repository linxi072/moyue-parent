package com.moyue.common.obs;

import com.moyue.common.Constants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Feign 透传拦截器（由 ObservabilityAutoConfiguration 注册）：
 * 把当前请求的 traceId / userId 注入到下游 Feign 调用的请求头，
 * 实现跨服务链路串联（网关已把 X-Trace-Id 注入入站请求，TraceIdFilter 写入 MDC）。
 */
public class TraceIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
            MDC.put(TraceIdFilter.MDC_TRACE_ID, traceId);
        }
        template.header(Constants.TRACE_ID_HEADER, traceId);

        String uid = MDC.get(TraceIdFilter.MDC_USER_ID);
        if (uid != null && !uid.isBlank()) {
            template.header(Constants.USER_ID_HEADER, uid);
        }
    }
}

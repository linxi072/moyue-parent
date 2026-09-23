package com.moyue.common.obs;

import com.moyue.common.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 服务端 TraceId 过滤器（Servlet 环境，由 ObservabilityAutoConfiguration 注册）：
 * 1. 读取入站 X-Trace-Id；缺失则生成；
 * 2. 写入 SLF4J MDC（traceId / userId），使本条请求的全部日志自带追踪号；
 * 3. 回写响应头 X-Trace-Id，便于前端/网关联调；
 * 4. 请求结束后清理 MDC，避免线程复用污染下一条请求。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    static final String MDC_TRACE_ID = "traceId";
    static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(Constants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
        }
        MDC.put(MDC_TRACE_ID, traceId);

        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid != null && !uid.isBlank()) {
            MDC.put(MDC_USER_ID, uid);
        }

        try {
            response.setHeader(Constants.TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_USER_ID);
        }
    }
}

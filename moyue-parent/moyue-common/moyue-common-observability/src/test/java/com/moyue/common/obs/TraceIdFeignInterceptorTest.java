package com.moyue.common.obs;

import com.moyue.common.Constants;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TraceIdFeignInterceptorTest {

    private final TraceIdFeignInterceptor interceptor = new TraceIdFeignInterceptor();

    @Test
    void addsTraceIdHeaderAndGeneratesIfMissing() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        String tid = template.headers().get(Constants.TRACE_ID_HEADER).stream().findFirst().orElse(null);
        assertNotNull(tid, "X-Trace-Id header must be present");
        assertEquals(32, tid.length());
    }

    @Test
    void propagatesMdcTraceId() {
        String existing = "feedfacefeedfacefeedfacefeedface";
        MDC.put(TraceIdFilter.MDC_TRACE_ID, existing);
        try {
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);
            String tid = template.headers().get(Constants.TRACE_ID_HEADER).stream().findFirst().orElse(null);
            assertEquals(existing, tid);
        } finally {
            MDC.remove(TraceIdFilter.MDC_TRACE_ID);
        }
    }
}

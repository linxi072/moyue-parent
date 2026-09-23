package com.moyue.common.obs;

import com.moyue.common.Constants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenAbsentAndEchoesInResponse() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> assertNotNull(MDC.get(TraceIdFilter.MDC_TRACE_ID),
                "MDC traceId should be set during chain");
        filter.doFilter(req, res, chain);
        String header = res.getHeader(Constants.TRACE_ID_HEADER);
        assertNotNull(header, "response must echo X-Trace-Id");
        assertEquals(32, header.length());
        assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID), "MDC must be cleared after request");
    }

    @Test
    void propagatesExistingTraceId() throws Exception {
        String incoming = "feedfacefeedfacefeedfacefeedface";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(Constants.TRACE_ID_HEADER, incoming);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        assertEquals(incoming, res.getHeader(Constants.TRACE_ID_HEADER));
        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void putsUserIdIntoMdcWhenPresentAndCleansUp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(Constants.USER_ID_HEADER, "7001");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> assertEquals("7001", MDC.get(TraceIdFilter.MDC_USER_ID));
        filter.doFilter(req, res, chain);
        assertNull(MDC.get(TraceIdFilter.MDC_USER_ID), "MDC userId must be cleared after request");
    }
}

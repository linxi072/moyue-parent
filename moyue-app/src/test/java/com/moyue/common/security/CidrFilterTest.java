package com.moyue.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CidrFilter 单测（Mock 环境，不加载 Spring 容器）。
 * 通过反射注入 allowGatewayCidr，验证 CIDR 放行/拦截、localhost、/actuator 排除与空配置 no-op。
 */
class CidrFilterTest {

    private CidrFilter filterWith(String cidr) throws Exception {
        CidrFilter f = new CidrFilter();
        setField(f, "allowGatewayCidr", cidr);
        return f;
    }

    @Test
    @DisplayName("来源在允许 CIDR 内 → 放行")
    void allowedCidr_passes() throws Exception {
        CidrFilter f = filterWith("10.0.0.0/16");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/books/1");
        req.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertNotNull(chain.getRequest());
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
    }

    @Test
    @DisplayName("来源在 CIDR 外 → 403")
    void outsideCidr_rejected403() throws Exception {
        CidrFilter f = filterWith("10.0.0.0/16");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/books/1");
        req.setRemoteAddr("192.168.1.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertNull(chain.getRequest());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getStatus());
    }

    @Test
    @DisplayName("/actuator 排除过滤")
    void actuator_excluded() throws Exception {
        CidrFilter f = filterWith("10.0.0.0/16");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/prometheus");
        req.setRemoteAddr("192.168.1.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertNotNull(chain.getRequest());
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
    }

    @Test
    @DisplayName("127.0.0.1 始终放行")
    void localhost_alwaysAllowed() throws Exception {
        CidrFilter f = filterWith("10.0.0.0/16");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/books/1");
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
    }

    @Test
    @DisplayName("配置为空 → no-op 放行（dev/test/H2 安全）")
    void emptyConfig_noOp() throws Exception {
        CidrFilter f = filterWith("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/books/1");
        req.setRemoteAddr("192.168.1.5");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
    }

    @Test
    @DisplayName("remoteAddr 为空（MockMvc）→ 放行")
    void nullRemoteAddr_allowed() throws Exception {
        CidrFilter f = filterWith("10.0.0.0/16");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/books/1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

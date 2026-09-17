package com.moyue.common.security;

import com.moyue.common.Constants;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InternalAuthInterceptor 单测（Mock 环境，不加载 Spring 容器）。
 * 通过反射注入 dev 默认令牌，验证内部端点令牌校验逻辑。
 */
class InternalAuthInterceptorTest {

    private final InternalAuthInterceptor interceptor = new InternalAuthInterceptor();

    @BeforeEach
    void setUp() throws Exception {
        setField(interceptor, "internalToken", "dev-internal-token");
    }

    @Test
    @DisplayName("缺失令牌 → 403")
    void missingToken_rejected403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/internal/points/award");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getStatus());
    }

    @Test
    @DisplayName("合法令牌 → 放行")
    void validToken_allowed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/internal/points/award");
        req.addHeader(Constants.SERVICE_TOKEN_HEADER, "dev-internal-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(req, res, new Object()));
    }

    @Test
    @DisplayName("伪造令牌 → 403")
    void wrongToken_rejected403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/internal/points/award");
        req.addHeader(Constants.SERVICE_TOKEN_HEADER, "evil-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req, res, new Object()));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getStatus());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

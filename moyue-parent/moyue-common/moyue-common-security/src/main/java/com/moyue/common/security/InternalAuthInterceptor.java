package com.moyue.common.security;

import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 内部端点鉴权拦截器：仅注册于 {@code /api/v1/internal/**}（由 {@link com.moyue.common.WebMvcConfig} 限定路径）。
 * 校验 {@link Constants#SERVICE_TOKEN_HEADER}（X-Service-Token），该头由服务间 Feign
 * {@code ServiceTokenRequestInterceptor} 自动注入；缺失或非法 → 403，
 * 消除「伪造网关注入头直接调用内部端点」的漏洞。
 * <p>非内部路径不会进入本拦截器，由 {@code WebMvcConfig} 的注册路径保证；
 * 令牌取值与注入侧一致，缺省 dev 本地令牌（生产经 {@code MOYUE_INTERNAL_TOKEN} 环境变量覆盖）。</p>
 */
@Component
public class InternalAuthInterceptor implements HandlerInterceptor {

    /** 内部调用令牌（与 ServiceTokenRequestInterceptor 注入侧保持一致） */
    @Value("${moyue.security.internalToken:dev-internal-token}")
    private String internalToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String token = request.getHeader(Constants.SERVICE_TOKEN_HEADER);
        if (!StringUtils.hasText(internalToken) || !internalToken.equals(token)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(ResultCode.FORBIDDEN)));
            return false;
        }
        return true;
    }
}

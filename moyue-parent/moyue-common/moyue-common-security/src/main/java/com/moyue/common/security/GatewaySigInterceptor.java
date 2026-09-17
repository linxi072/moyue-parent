package com.moyue.common.security;

import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.SecuritySignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 网关来源与信任头签名校验（P1，纵深防御）。
 * 仅当 {@code moyue.security.enforceSourceCheck=true} 时启用；默认关闭（no-op），避免误伤服务间 Feign 直连。
 * 启用后：
 * <ol>
 *   <li>非 {@code /api/v1/internal/**} 路径要求 {@link Constants#FORWARDED_BY_HEADER}=moyue-gateway（标识请求经网关）；</li>
 *   <li>若配置了 {@code moyue.security.gatewaySigSecret}，校验 {@link Constants#GATEWAY_SIG_HEADER}
 *       （网关对 method+path+timestamp 的 HMAC-SHA256），缺失或不符 → 403。</li>
 * </ol>
 * 业务侧仅做校验，签名由网关 {@code JwtAuthGlobalFilter} 注入。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GatewaySigInterceptor implements HandlerInterceptor {

    /** 是否启用来源/签名强校验（默认关闭，避免破坏服务间 Feign 直连，生产按需开启） */
    @Value("${moyue.security.enforceSourceCheck:false}")
    private boolean enforceSourceCheck;

    /** 网关信任头 HMAC 签名密钥（与网关侧一致）；留空则不校验签名 */
    @Value("${moyue.security.gatewaySigSecret:}")
    private String gatewaySigSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (!enforceSourceCheck) {
            return true; // 默认关闭，no-op
        }
        String path = request.getRequestURI();
        // 内部端点由 InternalAuthInterceptor 管令牌，此处不重复校验
        if (path != null && path.startsWith("/api/v1/internal/")) {
            return true;
        }
        if (!Constants.FORWARDED_BY_GATEWAY.equals(request.getHeader(Constants.FORWARDED_BY_HEADER))) {
            return reject(response);
        }
        if (StringUtils.hasText(gatewaySigSecret) && !verifySig(request)) {
            return reject(response);
        }
        return true;
    }

    private boolean verifySig(HttpServletRequest request) {
        String sig = request.getHeader(Constants.GATEWAY_SIG_HEADER);
        String ts = request.getHeader("X-Gateway-Ts");
        if (!StringUtils.hasText(sig) || !StringUtils.hasText(ts)) {
            return false;
        }
        String expected = SecuritySignUtil.sign(request.getMethod(), request.getRequestURI(), ts, gatewaySigSecret);
        return expected.equals(sig);
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(ResultCode.FORBIDDEN)));
        return false;
    }
}

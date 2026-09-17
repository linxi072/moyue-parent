package com.moyue.common.security;

import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关直连防护（网络层）：仅允许「网关网段 + 127.0.0.1/::1 + 配置白名单 CIDR」访问业务端口。
 * 非允许来源直接 403，消除「绕过网关直连业务端口」的漏洞。
 * <ul>
 *   <li>配置 {@code moyue.security.allowGatewayCidr} 为空（dev/test 默认）→ 本过滤器 no-op，便于本地与 H2 测试；
 *       生产由运维填入网关网段 + 集群 Pod/子网 CIDR，配合安全组双保险。</li>
 *   <li>{@code /actuator/**} 排除，避免 Prometheus scrape 被误伤。</li>
 *   <li>remoteAddr 为空（MockMvc / 本地）一律放行，避免测试误伤。</li>
 * </ul>
 * 仅 Servlet 环境注册（网关为 WebFlux，不加载本过滤器）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CidrFilter extends OncePerRequestFilter {

    /** 允许直连的来源 CIDR 列表（逗号分隔），留空表示放行（no-op） */
    @Value("${moyue.security.allowGatewayCidr:}")
    private String allowGatewayCidr;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!StringUtils.hasText(allowGatewayCidr)) {
            // dev/test/默认：不过滤，避免误伤本地与 H2 测试
            filterChain.doFilter(request, response);
            return;
        }
        if (isAllowed(normalize(request.getRemoteAddr()))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(ResultCode.FORBIDDEN)));
    }

    private boolean isAllowed(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return true; // MockMvc / 本地无 remoteAddr：放行
        }
        if ("127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            return true;
        }
        for (Cidr cidr : parseCidRs(allowGatewayCidr)) {
            if (cidr.matches(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    /** 规整 remoteAddr：去掉 IPv6 方括号与可能的端口 */
    private static String normalize(String addr) {
        if (addr == null) {
            return null;
        }
        if (addr.startsWith("[") && addr.endsWith("]")) {
            addr = addr.substring(1, addr.length() - 1);
        }
        int lastColon = addr.lastIndexOf(':');
        // 仅当存在多个冒号（IPv6）时不截断；单冒号视为 IPv4 端口
        if (lastColon > 0 && addr.indexOf(':') == lastColon) {
            addr = addr.substring(0, lastColon);
        }
        return addr;
    }

    private static List<Cidr> parseCidRs(String raw) {
        List<Cidr> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            int slash = part.indexOf('/');
            try {
                if (slash < 0) {
                    result.add(new Cidr(part, 32));
                } else {
                    result.add(new Cidr(part.substring(0, slash), Integer.parseInt(part.substring(slash + 1))));
                }
            } catch (NumberFormatException | UnknownHostException ignored) {
                // 非法 CIDR 忽略
            }
        }
        return result;
    }

    /** 单个 CIDR 匹配器（IPv4 + IPv6，纯位运算，无第三方依赖） */
    private static final class Cidr {
        private final byte[] network;
        private final byte[] mask;

        Cidr(String ip, int prefix) throws UnknownHostException {
            this.network = InetAddress.getByName(ip).getAddress();
            this.mask = buildMask(prefix, this.network.length);
        }

        boolean matches(String remoteAddr) {
            try {
                byte[] remote = InetAddress.getByName(remoteAddr).getAddress();
                if (remote.length != network.length) {
                    return false;
                }
                for (int i = 0; i < remote.length; i++) {
                    if ((remote[i] & mask[i]) != (network[i] & mask[i])) {
                        return false;
                    }
                }
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }

        private static byte[] buildMask(int prefix, int bytes) {
            byte[] m = new byte[bytes];
            for (int i = 0; i < bytes; i++) {
                int bits = Math.min(8, Math.max(0, prefix - i * 8));
                m[i] = (byte) (0xFF << (8 - bits));
            }
            return m;
        }
    }
}

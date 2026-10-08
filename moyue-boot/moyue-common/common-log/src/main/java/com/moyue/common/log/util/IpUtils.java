package com.moyue.common.log.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 与 UA 解析工具。
 *
 * <p>取 IP 时按 Nginx 常用代理头顺序回溯，避免拿到网关内网地址。
 *
 * @author moyue
 */
@Slf4j
public final class IpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private IpUtils() {
    }

    /**
     * 获取客户端真实 IP。
     *
     * @param request 请求
     * @return IP 字符串
     */
    public static String getIp(HttpServletRequest request) {
        if (request == null) {
            return LOCALHOST_IPV4;
        }
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"
                int idx = ip.indexOf(',');
                return (idx > 0 ? ip.substring(0, idx) : ip).trim();
            }
        }
        String ip = request.getRemoteAddr();
        if (LOCALHOST_IPV6.equals(ip)) {
            return LOCALHOST_IPV4;
        }
        return ip;
    }

    /**
     * 解析 User-Agent，返回 "操作系统 / 浏览器" 的简要描述。
     *
     * @param request 请求
     * @return UA 描述
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ua = request.getHeader("User-Agent");
        return ua == null ? "" : ua;
    }

    /**
     * 本地主机名，供服务监控展示。
     *
     * @return 主机名
     */
    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}

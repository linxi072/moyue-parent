package com.moyue.boot.filter;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.security.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求包装：把解析出的身份写回请求头。
 *
 * <p>存在的意义是<b>抹平形态差异</b>：cloud 形态下身份头由网关写入并随 HTTP 转发，
 * 单体形态下没有这一跳，于是由本包装器就地补上，业务代码读
 * {@code X-User-Id} 的写法在两种形态下完全一致。
 *
 * @author moyue
 */
public class UserHeaderRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> headers;

    public UserHeaderRequestWrapper(HttpServletRequest request, LoginUser user) {
        super(request);
        this.headers = new LinkedHashMap<>();
        headers.put(Constants.HEADER_USER_ID, String.valueOf(user.getUserId()));
        headers.put(Constants.HEADER_USER_NAME, nvl(user.getUsername()));
        headers.put(Constants.HEADER_USER_TYPE, String.valueOf(nvl(user.getUserType())));
        headers.put(Constants.HEADER_USER_ROLE, String.join(",", user.getRoleKeys()));
    }

    @Override
    public String getHeader(String name) {
        String value = headers.get(name);
        return value != null ? value : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = headers.get(name);
        return value != null ? Collections.enumeration(java.util.List.of(value)) : super.getHeaders(name);
    }

    private static String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

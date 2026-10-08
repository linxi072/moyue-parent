package com.moyue.auth.service;

import com.moyue.auth.domain.dto.LoginRequest;
import com.moyue.auth.domain.dto.RegisterRequest;
import com.moyue.auth.domain.vo.TokenVO;

/**
 * 认证服务：登录 / 注册 / 刷新令牌。
 *
 * @author moyue
 */
public interface AuthService {

    /**
     * 统一登录。
     *
     * @param request   登录请求
     * @param ip        客户端 IP
     * @param userAgent 浏览器 UA
     * @return 令牌响应
     */
    TokenVO login(LoginRequest request, String ip, String userAgent);

    /**
     * C 端注册（读者 / 作者）。
     *
     * @param request 注册请求
     * @return 新用户 ID
     */
    Long register(RegisterRequest request);

    /**
     * 刷新令牌。
     *
     * @param refreshToken refresh token
     * @return 新的令牌响应
     */
    TokenVO refresh(String refreshToken);

    /** 账号是否已占用（注册校验用） */
    boolean usernameTaken(String username);
}

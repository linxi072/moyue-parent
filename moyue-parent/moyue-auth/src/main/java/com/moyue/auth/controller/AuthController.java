package com.moyue.auth.controller;

import com.moyue.auth.dto.LoginReq;
import com.moyue.auth.dto.RefreshReq;
import com.moyue.auth.dto.RegisterReq;
import com.moyue.auth.service.AuthService;
import com.moyue.auth.vo.LoginVO;
import com.moyue.auth.vo.UserInfoVO;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.constants.Constants;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口：登录 / 注册 / 刷新 / 当前用户资料。
 * 映射统一包含 BasePath /api/v1，与网关路由谓词保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** 手机号 + 密码登录，返回 accessToken / refreshToken */
    @PostMapping("/auth/login")
    public R<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req));
    }

    /** 注册（演示版不校验短信验证码） */
    @PostMapping("/auth/register")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        authService.register(req);
        return R.ok();
    }

    /** 用 refreshToken 换取新的 accessToken（白名单免鉴权） */
    @PostMapping("/auth/refresh")
    public R<LoginVO> refresh(@RequestBody RefreshReq req) {
        return R.ok(authService.refresh(req));
    }

    /** 获取当前登录用户资料（读取网关注入的 X-User-Id 头） */
    @GetMapping("/users/me")
    public R<UserInfoVO> me(HttpServletRequest request) {
        String userId = request.getHeader(Constants.USER_ID_HEADER);
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return R.ok(authService.me(Long.valueOf(userId.trim())));
    }
}

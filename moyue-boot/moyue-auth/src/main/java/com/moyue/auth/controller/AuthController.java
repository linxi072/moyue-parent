package com.moyue.auth.controller;

import com.moyue.auth.domain.dto.LoginRequest;
import com.moyue.auth.domain.dto.RegisterRequest;
import com.moyue.auth.domain.vo.TokenVO;
import com.moyue.auth.service.AuthService;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.log.util.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（8090）。
 *
 * <p>三个端点均为白名单免鉴权：网关与 common-security 的 permit-paths 都要放行
 * （见 WhitelistProperties 注释里的「两份配置必须一致」说明）。
 *
 * <p>本控制器承载架构说明书 ① 用户管理域的登录端点 {@code POST /api/v1/system/login}，
 * 逻辑归属用户管理域、物理部署在 auth 服务（ADR-3），已在缺口说明登记为 G-9。
 *
 * @author moyue
 */
@Tag(name = "认证", description = "统一登录 / 注册 / 刷新令牌")
@Validated
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "统一登录", description = "BCrypt 校验，签发携带 user_type 与角色的令牌")
    @Log(title = "登录", businessType = BusinessType.OTHER, saveResponseData = false)
    @PostMapping("/login")
    public R<TokenVO> login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest) {
        return R.ok(authService.login(request,
                IpUtils.getIp(httpRequest), IpUtils.getUserAgent(httpRequest)));
    }

    @Operation(summary = "C 端注册", description = "仅允许注册读者 / 作者，运营账号需后台创建")
    @Log(title = "注册", businessType = BusinessType.INSERT, saveRequestData = false)
    @PostMapping("/register")
    public R<Long> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @Operation(summary = "刷新令牌", description = "用 refresh token 换发新的 access token，并重新加载角色权限")
    @PostMapping("/refresh")
    public R<TokenVO> refresh(@RequestHeader(value = "X-Refresh-Token", required = false)
                              String refreshToken) {
        return R.ok(authService.refresh(refreshToken));
    }

    @Operation(summary = "账号占用检查", description = "注册页实时校验")
    @GetMapping("/username-taken")
    public R<Boolean> usernameTaken(@RequestParam String username) {
        return R.ok(authService.usernameTaken(username));
    }
}

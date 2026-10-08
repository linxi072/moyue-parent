package com.moyue.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一登录请求。
 *
 * @author moyue
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录账号（运营为 username，C 端可为手机号） */
    @NotBlank(message = "登录账号不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码标识，开启验证码时必填 */
    private String captchaKey;

    /** 验证码值 */
    private String captchaCode;
}

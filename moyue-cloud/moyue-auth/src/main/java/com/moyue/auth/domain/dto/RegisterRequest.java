package com.moyue.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端注册请求（读者 / 作者）。
 *
 * <p>注册只允许落到 user_type ∈ {1 读者, 2 作者}：<strong>运营账号不允许自助注册</strong>，
 * 必须由后台用户管理创建并分配角色，否则任何人都能注册出运营主体。
 *
 * @author moyue
 */
@Data
public class RegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 20, message = "账号长度需为 4~20 位")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需为 8~32 位")
    private String password;

    /** 手机号 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 主体性质：1 读者 / 2 作者 */
    private Integer userType;

    /** 昵称，缺省取账号 */
    private String nickname;
}

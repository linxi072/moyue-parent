package com.moyue.system.vo;

import lombok.Data;

/**
 * 运营后台登录响应：签发 role=3 的 accessToken，由网关透传 X-User-Role 后通过 AdminRoleInterceptor。
 */
@Data
public class AdminLoginVO {

    /** 访问令牌（无状态，role=3） */
    private String accessToken;

    /** 用户 ID（sys_user.id，作为 JWT subject，网关透传为 X-User-Id） */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 角色值（恒为 3，管理员） */
    private Integer role;
}

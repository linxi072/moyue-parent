package com.moyue.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线用户 VO：Redis 会话 {@code moyue:online:{md5(token)}} 的对外投影。
 * tokenId 为登录令牌的 MD5 摘要（不回传原始令牌，防泄露）。
 */
@Data
public class OnlineUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话标识 = md5(accessToken) */
    private String tokenId;

    /** 用户 ID → sys_user.id */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 登录 IP */
    private String ip;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 浏览器 UA */
    private String userAgent;
}

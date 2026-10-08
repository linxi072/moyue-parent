package com.moyue.auth.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录令牌响应。
 *
 * @author moyue
 */
@Data
@Builder
public class TokenVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** access token */
    private String accessToken;

    /** refresh token */
    private String refreshToken;

    /** access token 有效期（秒） */
    private long expiresIn;

    /** 用户 ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 主体性质：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 角色标识集合 */
    private Set<String> roles;
}

package com.moyue.api.account.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 用户数据传输对象（跨服务共享）。
 */
@Data
public class UserDTO implements Serializable {

    private Long id;

    private String phone;

    private String nickname;

    private String avatarUrl;

    /** 邮箱（触达渠道：邮件；可空） */
    private String email;

    /** 设备推送令牌（触达渠道：推送；可空） */
    private String deviceToken;

    /** 1 读者 / 2 作者 / 3 管理员 */
    private Integer role;

    /** 0 禁用 / 1 正常 */
    private Integer status;
}

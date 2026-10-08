package com.moyue.common.log.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志传输对象，对应 {@code sys_login_log} 表。
 *
 * @author moyue
 */
@Data
@Builder
public class LoginLogDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 登录账号 */
    private String username;

    /** 用户 ID，登录失败时为 null */
    private Long userId;

    /** 客户端 IP */
    private String ip;

    /** 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 主体类型：1 读者 / 2 作家 / 3 运营 */
    private Integer userType;

    /** 登录状态：0 成功 / 1 失败 */
    private Integer status;

    /** 提示消息 */
    private String message;

    /** 登录时间 */
    private LocalDateTime loginTime;
}

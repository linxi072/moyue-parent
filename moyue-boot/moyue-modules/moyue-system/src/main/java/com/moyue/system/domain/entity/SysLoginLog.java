package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;


/**
 * 登录日志实体（对应 common-log 的 LoginLogDTO）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    private String username;

    /** 用户 ID，登录失败时为 NULL */
    private Long userId;

    /** 主体类型：1 读者 / 2 作者 / 3 运营 */
    private Integer userType;

    /** 登录 IP */
    private String ip;

    /** IP 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 登录状态：0 成功 / 1 失败 */
    private Integer status;

    /** 提示消息 */
    private String message;

    /** 登录时间 */
    private java.time.LocalDateTime loginTime;
}

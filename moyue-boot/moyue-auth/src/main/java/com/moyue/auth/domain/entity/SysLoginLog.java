package com.moyue.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志（映射到 sys_login_log，供 auth 独立落库）。
 *
 * <p>与 moyue-system 的同名实体是<strong>两份独立定义</strong>：两个进程各自写同一张表，
 * 表结构由 common-migration 的 V16 统一保证，实体重复换取的是模块解耦。
 *
 * @author moyue
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 用户 ID */
    private Long userId;

    /** 主体性质 */
    private Integer userType;

    /** 登录 IP */
    private String ip;

    /** 登录地点 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 状态：0 成功 / 1 失败 */
    private Integer status;

    /** 提示消息 */
    private String message;

    /** 登录时间 */
    private LocalDateTime loginTime;
}

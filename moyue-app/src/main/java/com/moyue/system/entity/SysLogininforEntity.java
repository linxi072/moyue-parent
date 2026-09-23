package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统登录日志实体，映射 sys_logininfor 表（V14）。
 * <p>后台登录（/api/v1/system/login）成功与失败均异步落一条记录。</p>
 */
@Data
@TableName("sys_logininfor")
public class SysLogininforEntity {

    /** 日志主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录 IP */
    private String ip;

    /** 浏览器 UA */
    private String userAgent;

    /** 登录状态：0 成功 / 1 失败 */
    private Integer status;

    /** 提示信息，如 登录成功 / 账号或密码错误 */
    private String msg;

    /** 登录时间 */
    private LocalDateTime loginTime;
}

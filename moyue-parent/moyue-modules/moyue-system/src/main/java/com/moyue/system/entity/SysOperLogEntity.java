package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体，映射 sys_oper_log 表（V14）。
 * <p>由 {@code @Log} 注解切面异步写入；日志保留策略为物理删除（按 ID 批量删），
 * param / result 字段在切面侧截断 2000 字符，避免超长 SQL / 响应撑爆 TEXT。</p>
 */
@Data
@TableName("sys_oper_log")
public class SysOperLogEntity {

    /** 日志主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务模块，如 字典管理 */
    private String module;

    /** 业务类型：0 其它 / 1 新增 / 2 修改 / 3 删除 / 4 导出 / 5 强退 / 6 生成代码 */
    private Integer businessType;

    /** HTTP 请求方式：GET/POST/PUT/DELETE */
    private String requestMethod;

    /** 请求 URL */
    private String url;

    /** 操作人员 ID → sys_user.id */
    private Long operatorId;

    /** 操作人员名称 */
    private String operatorName;

    /** 操作 IP */
    private String ip;

    /** 请求参数（截断 2000 字符） */
    private String param;

    /** 返回结果（截断 2000 字符） */
    private String result;

    /** 操作状态：0 成功 / 1 失败 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Integer costMs;

    /** 操作时间 */
    private LocalDateTime operTime;
}

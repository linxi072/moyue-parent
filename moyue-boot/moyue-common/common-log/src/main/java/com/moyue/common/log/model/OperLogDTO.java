package com.moyue.common.log.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志传输对象。
 *
 * <p>结构与 common-migration 中 {@code V16__audit_log.sql} 的 {@code sys_oper_log} 表一一对应，
 * 由 moyue-system 的审计日志域负责持久化。
 *
 * @author moyue
 */
@Data
@Builder
public class OperLogDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志主键（雪花 ID，由持久层生成） */
    private Long id;

    /** 模块标题 */
    private String title;

    /** 业务类型 */
    private Integer businessType;

    /** 方法全限定名 */
    private String method;

    /** 请求方式 GET/POST/PUT/DELETE */
    private String requestMethod;

    /** 操作人类别 */
    private Integer operatorType;

    /** 操作人账号 */
    private String operName;

    /** 操作人 ID */
    private Long operId;

    /** 请求 URL */
    private String operUrl;

    /** 客户端 IP */
    private String operIp;

    /** 归属地 */
    private String operLocation;

    /** 请求参数 */
    private String operParam;

    /** 响应结果 */
    private String jsonResult;

    /** 操作状态：0 正常 / 1 异常 */
    private Integer status;

    /** 错误消息 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 操作时间 */
    private LocalDateTime operTime;
}

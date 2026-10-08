package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;


/**
 * 操作日志实体（对应 common-log 的 OperLogDTO）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模块标题 */
    private String title;

    /** 业务类型：0 其它 / 1 新增 / 2 修改 / 3 删除 / 4 授权 / 5 导出 / 6 导入 / 7 强退 / 8 生成代码 / 9 清空 / 10 审核 / 11 上下线 */
    private Integer businessType;

    /** 方法全限定名 */
    private String method;

    /** HTTP 方法 */
    private String requestMethod;

    /** 操作人类别：0 其它 / 1 读者 / 2 作者 / 3 后台运营 / 4 系统内部 */
    private Integer operatorType;

    /** 操作人账号 */
    private String operName;

    /** 操作人 ID */
    private Long operId;

    /** 请求 URL */
    private String operUrl;

    /** 客户端 IP */
    private String operIp;

    /** IP 归属地 */
    private String operLocation;

    /** 请求参数（截断 4000 字符） */
    private String operParam;

    /** 响应结果（截断 2000 字符） */
    private String jsonResult;

    /** 操作状态：0 正常 / 1 异常 */
    private Integer status;

    /** 错误消息 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 操作时间 */
    private java.time.LocalDateTime operTime;
}

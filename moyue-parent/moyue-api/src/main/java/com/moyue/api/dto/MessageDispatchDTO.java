package com.moyue.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 消息触达请求 DTO（跨服务共享）：业务方（risk / content 等）提交一次触达。
 */
@Data
public class MessageDispatchDTO implements Serializable {

    /** 接收用户 ID */
    private Long userId;

    /** 模板编码，如 AUDIT_PASS / AUDIT_REJECT / REPORT_RESULT */
    private String templateCode;

    /** 模板占位符参数（{key} → value） */
    private Map<String, String> params;

    /** 指定渠道：1 站内信/2 邮件/3 短信/4 推送；为空则取模板默认渠道 */
    private List<Integer> channels;

    /** 可选：覆盖用户资料的收件邮箱 */
    private String targetEmail;

    /** 可选：覆盖用户资料的收件手机号 */
    private String targetPhone;

    /** 业务类型，如 AUDIT / REPORT */
    private String bizType;

    /** 业务主键 */
    private Long bizId;
}

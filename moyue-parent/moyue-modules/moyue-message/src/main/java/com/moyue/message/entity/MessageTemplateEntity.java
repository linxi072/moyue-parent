package com.moyue.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息模板实体，映射 message_template 表（V13）。
 *
 * <p>模板占位符支持 {@code {key}} 与 {@code ${key}} 两种写法，由
 * {@code MessageTemplateService} 于分发时按 {@code params} 替换。</p>
 */
@Data
@TableName("message_template")
public class MessageTemplateEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模板编码，如 AUDIT_PASS / AUDIT_REJECT / REPORT_RESULT */
    private String code;

    /** 模板名称 */
    private String name;

    /** 标题模板，占位符 {param} */
    private String titleTpl;

    /** 内容模板，占位符 {param} */
    private String contentTpl;

    /** 默认渠道，逗号分隔：1 站内信 / 2 邮件 / 3 短信 / 4 推送 */
    private String channels;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 逻辑删除标记：0 未删除 / 1 已删除 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

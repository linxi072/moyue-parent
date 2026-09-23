package com.moyue.api.search.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * AI 客服问答索引文档 DTO（跨服务共享）：moyue-ai 推送至 moyue-search 的问答索引载荷。
 * 对应 ES 索引 {@code moyue-qa} 的字段；一轮对话（用户提问 + 助手回复）合并为一条文档，
 * {@code messageId} 取助手回复消息 ID，作为文档 _id，重复推送即覆盖更新（幂等）。
 */
@Data
public class QaIndexDTO implements Serializable {

    /** 助手回复消息 ID → ai_message.id（作为 ES 文档 _id，幂等覆盖） */
    private Long messageId;

    /** 会话 ID → ai_session.id（term 过滤 / 按会话删除用） */
    private Long sessionId;

    /** 用户提问内容（ai_message.role=1 的 content） */
    private String question;

    /** 助手回复内容（ai_message.role=2 的 content） */
    private String answer;

    /** 回复时间（取助手消息的 create_time） */
    private LocalDateTime createTime;
}

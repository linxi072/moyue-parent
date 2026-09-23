package com.moyue.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 客服消息实体，映射 ai_message 表。
 * role：1 用户提问 / 2 助手回复。
 */
@Data
@TableName("ai_message")
public class AiMessageEntity {

    /** 消息主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID → ai_session.id */
    private Long sessionId;

    /** 角色：1 用户 / 2 助手 */
    private Integer role;

    /** 消息内容 */
    private String content;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 发送时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

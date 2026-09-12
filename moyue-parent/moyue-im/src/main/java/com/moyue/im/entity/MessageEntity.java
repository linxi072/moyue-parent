package com.moyue.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体，映射 chat_message 表。
 */
@Data
@TableName("chat_message")
public class MessageEntity {

    /** 消息主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID → chat_conversation.id */
    private Long conversationId;

    /** 发送人 → user.id */
    private Long senderId;

    /** 消息内容 */
    private String content;

    /** 类型：1 文本 / 2 图片 / 3 系统 */
    private Integer type;

    /** 状态：0 已发送 / 1 已读 */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 发送时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

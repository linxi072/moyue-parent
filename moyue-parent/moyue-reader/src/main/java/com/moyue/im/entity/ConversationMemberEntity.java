package com.moyue.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话成员关系实体，映射 chat_conversation_member 表。
 */
@Data
@TableName("chat_conversation_member")
public class ConversationMemberEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID → chat_conversation.id */
    private Long conversationId;

    /** 成员 ID → user.id */
    private Long userId;

    /** 角色：1 群主 / 2 成员 */
    private Integer role;

    /** 最后已读消息 ID */
    private Long lastReadMessageId;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 加入时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

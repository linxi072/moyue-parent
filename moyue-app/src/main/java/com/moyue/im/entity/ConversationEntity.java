package com.moyue.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体，映射 chat_conversation 表。
 */
@Data
@TableName("chat_conversation")
public class ConversationEntity {

    /** 会话主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 类型：1 单聊 / 2 群聊 */
    private Integer type;

    /** 群聊名称；单聊为空 */
    private String title;

    /** 群主 / 创建人 */
    private Long ownerId;

    /** 最近一条消息预览 */
    private String lastMessage;

    /** 最近消息时间 */
    private LocalDateTime lastMessageTime;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

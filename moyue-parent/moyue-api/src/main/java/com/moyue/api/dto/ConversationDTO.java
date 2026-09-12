package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 会话数据传输对象（跨服务共享）。
 * 单聊 title 为空，前端按对方昵称展示；群聊 title 为群名。
 */
@Data
public class ConversationDTO implements Serializable {

    private Long id;

    /** 1 单聊 / 2 群聊 */
    private Integer type;

    /** 群聊名称；单聊可为空 */
    private String title;

    /** 群主 / 创建人 */
    private Long ownerId;

    /** 成员 ID 列表（群聊） */
    private List<Long> memberIds;

    /** 最近一条消息预览 */
    private String lastMessagePreview;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

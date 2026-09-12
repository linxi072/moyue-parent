package com.moyue.im.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建会话请求体。
 */
@Data
public class CreateConversationRequest {

    /** 1 单聊 / 2 群聊 */
    private Integer type;

    /** 群聊名称；单聊可空 */
    private String title;

    /** 创建人 / 群主 */
    private Long ownerId;

    /** 成员 ID 列表（单聊为对方，群聊为其余成员） */
    private List<Long> memberIds;
}

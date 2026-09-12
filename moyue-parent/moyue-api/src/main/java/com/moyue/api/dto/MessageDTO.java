package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 聊天消息数据传输对象（跨服务共享）。
 */
@Data
public class MessageDTO implements Serializable {

    private Long id;

    private Long conversationId;

    private Long senderId;

    private String senderName;

    private String content;

    /** 1 文本 / 2 图片 / 3 系统 */
    private Integer type;

    /** 0 已发送 / 1 已读 */
    private Integer status;

    private LocalDateTime createTime;
}

package com.moyue.im.dto;

import lombok.Data;

/**
 * 发送消息请求体。
 */
@Data
public class SendMessageRequest {

    /** 发送人 ID */
    private Long senderId;

    /** 消息内容 */
    private String content;

    /** 消息类型：1 文本 / 2 图片 / 3 系统，默认 1 */
    private Integer type;
}

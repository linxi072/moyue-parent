package com.moyue.bookshelf.websocket;

import lombok.Data;

import java.io.Serializable;

/**
 * 推送至书架 WebSocket 客户端的消息体（JSON）。
 * 由 {@link com.moyue.bookshelf.controller.WsNotifyController} 在收到内容域通知后构造并广播。
 */
@Data
public class BookshelfPushMessage implements Serializable {

    /** 用户 ID */
    private Long userId;

    /** 书籍 ID */
    private Long bookId;

    /** 变更动作：ADD / REMOVE / PROGRESS / LISTEN_PROGRESS */
    private String action;

    /** 事件序号（与内容域一致，供前端幂等 / 排序） */
    private long eventId;

    /** 事件时间戳（ms） */
    private long timestamp;
}

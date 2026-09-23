package com.moyue.api.social.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 书架变更 WebSocket 通知负载（跨服务共享，P2-D）。
 * moyue-content 经 {@code WsNotifyClient} 推送给 moyue-social 内部端点
 * {@code /api/v1/internal/ws/bookshelf/notify}，由 {@code BookshelfWebSocketHandler}
 * 向目标用户的在线端广播。字段与 {@code BookshelfChangedEvent} 一一对应。
 */
@Data
public class BookshelfNotifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标用户 ID（接收方） */
    private Long userId;

    /** 书籍 ID */
    private Long bookId;

    /** 动作：ADD / REMOVE / PROGRESS / LISTEN */
    private String action;

    /** 该用户单调递增的变更序号（由 BookshelfEventPublisher 生成，供客户端去重/排序） */
    private Long eventId;

    /** 事件发生时间戳（毫秒） */
    private Long timestamp;
}

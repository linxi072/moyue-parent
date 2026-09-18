package com.moyue.read.event;

import org.springframework.context.ApplicationEvent;

/**
 * 书架变更领域事件（P2-D 实时同步）。
 * ReadService 在书架增删 / 阅读进度 / 听书进度写库后发布；
 * 由 {@link BookshelfWsNotifier} 监听并经 Feign 触发 moyue-social 近实时 WebSocket 推送。
 * 读路径（getShelf / getListenProgress）不发事件，避免无效推送。
 */
public class BookshelfChangedEvent extends ApplicationEvent {

    /** 动作常量：加入书架 / 移出书架 / 阅读进度 / 听书进度（与 BookshelfNotifyDTO.action 对齐） */
    public static final String ACTION_ADD = "ADD";
    public static final String ACTION_REMOVE = "REMOVE";
    public static final String ACTION_PROGRESS = "PROGRESS";
    public static final String ACTION_LISTEN = "LISTEN";

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final Long bookId;
    private final String action;
    private final Long eventId;
    private final Long occurredAt;

    /**
     * @param source   事件源（通常为发布器自身）
     * @param userId    用户 ID
     * @param bookId    书籍 ID
     * @param action    动作（见 ACTION_* 常量）
     * @param eventId   该用户单调自增的变更序号（由 BookshelfEventPublisher 生成）
     * @param timestamp 事件发生时间戳（毫秒）
     */
    public BookshelfChangedEvent(Object source, Long userId, Long bookId, String action,
                                 Long eventId, Long occurredAt) {
        super(source);
        this.userId = userId;
        this.bookId = bookId;
        this.action = action;
        this.eventId = eventId;
        this.occurredAt = occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getAction() {
        return action;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getOccurredAt() {
        return occurredAt;
    }
}

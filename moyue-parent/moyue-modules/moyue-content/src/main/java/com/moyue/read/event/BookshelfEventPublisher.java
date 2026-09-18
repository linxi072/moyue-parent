package com.moyue.read.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 书架变更事件发布器（P2-D）。
 * <p>为每个 user 维护一个单调递增的事件序号 eventId（AtomicLong），保证同一用户的多端事件有序、
 * 客户端可据此去重与排序。发布本身为同步 Spring 事件，由 {@link BookshelfWsNotifier} 监听处理。</p>
 */
@Component
public class BookshelfEventPublisher {

    private final ApplicationEventPublisher publisher;

    /** user -> 该用户单调自增的事件序号（从 1 开始） */
    private final ConcurrentHashMap<Long, AtomicLong> sequences = new ConcurrentHashMap<>();

    public BookshelfEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** 取该用户下一个事件序号（线程安全、单调递增） */
    private long nextEventId(Long userId) {
        return sequences.computeIfAbsent(userId, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 发布书架变更事件（在书架写库成功后调用）。
     *
     * @param userId  用户 ID
     * @param bookId  书籍 ID
     * @param action  动作（见 BookshelfChangedEvent.ACTION_*）
     * @return 已发布的事件（含生成的 eventId），便于调用方/测试断言
     */
    public BookshelfChangedEvent publish(Long userId, Long bookId, String action) {
        long eventId = nextEventId(userId);
        long ts = System.currentTimeMillis();
        BookshelfChangedEvent event = new BookshelfChangedEvent(this, userId, bookId, action, eventId, ts);
        publisher.publishEvent(event);
        return event;
    }
}

package com.moyue.read.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * BookshelfEventPublisher 单测（a）：验证同一用户 eventId 单调自增、不同用户各自独立计数。
 * 纯单测，不加载 Spring 上下文（publisher 用 Mockito mock）。
 */
class BookshelfEventPublisherTest {

    @Test
    @DisplayName("同一用户 eventId 单调递增；不同用户各自从 1 起算；事件携带必要字段")
    void eventIdMonotonicPerUser() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        BookshelfEventPublisher p = new BookshelfEventPublisher(publisher);

        BookshelfChangedEvent e1 = p.publish(1L, 100L, BookshelfChangedEvent.ACTION_ADD);
        BookshelfChangedEvent e2 = p.publish(1L, 100L, BookshelfChangedEvent.ACTION_PROGRESS);
        BookshelfChangedEvent e3 = p.publish(2L, 100L, BookshelfChangedEvent.ACTION_ADD);
        BookshelfChangedEvent e4 = p.publish(1L, 100L, BookshelfChangedEvent.ACTION_LISTEN);

        assertThat(e1.getEventId()).isEqualTo(1L);
        assertThat(e2.getEventId()).isEqualTo(2L);
        assertThat(e3.getEventId()).isEqualTo(1L); // 不同用户重置为 1
        assertThat(e4.getEventId()).isEqualTo(3L); // 用户 1 继续自增
        assertThat(e2.getEventId()).isGreaterThan(e1.getEventId());
        assertThat(e4.getEventId()).isGreaterThan(e2.getEventId());

        assertThat(e1.getUserId()).isEqualTo(1L);
        assertThat(e1.getBookId()).isEqualTo(100L);
        assertThat(e1.getAction()).isEqualTo(BookshelfChangedEvent.ACTION_ADD);
        assertThat(e1.getOccurredAt()).isPositive();
    }
}

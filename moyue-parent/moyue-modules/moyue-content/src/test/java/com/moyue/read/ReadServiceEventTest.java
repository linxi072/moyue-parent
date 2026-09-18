package com.moyue.read;

import com.moyue.api.social.client.WsNotifyClient;
import com.moyue.read.event.BookshelfChangedEvent;
import com.moyue.read.service.ReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReadService 书架变更事件发射集成测试（P2-D）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；通过 {@link EventCollector} 捕获
 * {@link BookshelfChangedEvent} 校验四个写入口均正确发射。
 * WsNotifyClient 以 @MockBean 替换，避免真实 Feign 调用，仅验证事件发射语义。
 *
 * 注意：action 以 String 常量（BookshelfChangedEvent.ACTION_*）表达，与 BookshelfChangedEvent /
 * ReadService 最终落地形态一致（早期枚举 Action 设计已废弃）。
 */
@SpringBootTest(classes = com.moyue.content.ContentApplication.class)
@ActiveProfiles("test")
@Import(ReadServiceEventTest.EventCaptureConfig.class)
class ReadServiceEventTest {

    @Autowired
    private ReadService readService;

    @MockBean
    private WsNotifyClient wsNotifyClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventCollector collector;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM bookshelf WHERE user_id = 7001");
        collector.clear();
    }

    @Test
    void addToShelf_emitsAddEvent() {
        readService.addToShelf(7001L, 3001L);
        assertThat(collector.events()).hasSize(1);
        assertEvent(0, 7001L, 3001L, BookshelfChangedEvent.ACTION_ADD);
    }

    @Test
    void removeFromShelf_emitsRemoveEvent() {
        readService.addToShelf(7001L, 3001L);
        collector.clear();
        readService.removeFromShelf(7001L, 3001L);
        assertEvent(0, 7001L, 3001L, BookshelfChangedEvent.ACTION_REMOVE);
    }

    @Test
    void updateProgress_emitsProgressEvent() {
        readService.addToShelf(7001L, 3002L);
        collector.clear();
        readService.updateProgress(7001L, 3002L, 4001L);
        assertEvent(0, 7001L, 3002L, BookshelfChangedEvent.ACTION_PROGRESS);
    }

    @Test
    void saveListenProgress_emitsListenEvent() {
        readService.addToShelf(7001L, 3003L);
        collector.clear();
        readService.saveListenProgress(7001L, 3003L, 4001L, 0, 0);
        assertEvent(0, 7001L, 3003L, BookshelfChangedEvent.ACTION_LISTEN);
    }

    private void assertEvent(int index, Long userId, Long bookId, String action) {
        BookshelfChangedEvent e = collector.events().get(index);
        assertThat(e.getUserId()).isEqualTo(userId);
        assertThat(e.getBookId()).isEqualTo(bookId);
        assertThat(e.getAction()).isEqualTo(action);
        assertThat(e.getEventId()).isPositive();
    }

    /** 测试用事件收集器：作为 ApplicationListener 注册进上下文，捕获发布的书架变更事件 */
    @Configuration
    static class EventCaptureConfig {
        @Bean
        public EventCollector eventCollector() {
            return new EventCollector();
        }
    }

    static class EventCollector implements ApplicationListener<BookshelfChangedEvent> {
        private final List<BookshelfChangedEvent> events = new ArrayList<>();

        void clear() {
            events.clear();
        }

        List<BookshelfChangedEvent> events() {
            return events;
        }

        @Override
        public void onApplicationEvent(BookshelfChangedEvent event) {
            events.add(event);
        }
    }
}

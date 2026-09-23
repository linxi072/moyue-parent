package com.moyue.read;

import com.moyue.read.event.BookshelfChangedEvent;
import com.moyue.read.event.BookshelfEventCollector;
import com.moyue.read.service.ReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReadService 书架变更事件发射集成测试（P2-D）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；通过 {@link BookshelfEventCollector}
 * （标准 {@code @Component} 扫描注册）捕获 {@link BookshelfChangedEvent} 校验四个写入口均正确发射。
 *
 * <p>早期版本用「测试类内嵌 @Configuration + @Import」注册收集器，会在 @SpringBootTest 下破坏配置引导、
 * 导致 ReadService 等 Bean 从上下文丢失；现改为普通 @Component，规避该 Spring Boot 已知坑。</p>
 *
 * <p>action 以 String 常量（BookshelfChangedEvent.ACTION_*）表达，与 BookshelfChangedEvent / ReadService
 * 最终落地形态一致（早期枚举 Action 设计已废弃）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReadServiceEventTest {

    @Autowired
    private ReadService readService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookshelfEventCollector collector;

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
}

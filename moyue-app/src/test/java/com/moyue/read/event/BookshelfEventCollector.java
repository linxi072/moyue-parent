package com.moyue.read.event;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用书架变更事件收集器（@Component，由统一组件扫描注册）。
 * <p>供 {@code ReadServiceEventTest} 等集成测试捕获 {@link BookshelfChangedEvent} 以断言发射语义。
 * 注意：早期实现曾用「测试类内嵌 {@code @Configuration} + {@code @Import}」注册收集器，
 * 但该写法在 {@code @SpringBootTest} 下会破坏配置引导、导致 {@link com.moyue.read.service.ReadService}
 * 等 Bean 从上下文丢失（Spring Boot 已知坑）；故改为普通 {@code @Component} 走标准扫描。</p>
 */
@Component
public class BookshelfEventCollector implements ApplicationListener<BookshelfChangedEvent> {

    private final List<BookshelfChangedEvent> events = new ArrayList<>();

    /** 清空已收集事件（测试 @BeforeEach 调用，避免用例间串扰） */
    public void clear() {
        events.clear();
    }

    /** 返回已收集的（不可变快照顺序）事件列表 */
    public List<BookshelfChangedEvent> events() {
        return events;
    }

    @Override
    public void onApplicationEvent(BookshelfChangedEvent event) {
        events.add(event);
    }
}

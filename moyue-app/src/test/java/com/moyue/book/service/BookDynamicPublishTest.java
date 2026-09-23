package com.moyue.book.service;

import com.moyue.api.account.client.UserClient;
import com.moyue.api.social.client.DynamicClient;
import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.common.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 书籍动态旁路发布集成测试（P2-E · moyue-content）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；{@link DynamicClient} 由 {@link MockBean} 替换，断言事件经监听器落库调用。
 * 覆盖：createBook 发布 type=1、updateBook 完结（status=2）发布 type=2、DynamicClient 抛异常时主流程仍成功（旁路非阻断）。
 * profile 固定为 {@code test}（见 src/test/resources/application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class BookDynamicPublishTest {

    @Autowired
    private BookService bookService;

    @MockBean
    private DynamicClient dynamicClient;

    /** 替换为本地 Mock，避免真实 Feign 调用用户服务（仅用于作者昵称反规范化快照） */
    @MockBean
    private UserClient userClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // 仅清理本用例创建的书籍（作者 900010/900011/900012）与动态表，保留种子数据，避免影响其它用例回归
        jdbcTemplate.update("DELETE FROM book WHERE author_id IN (900010, 900011, 900012)");
        jdbcTemplate.update("DELETE FROM user_dynamic");
        when(userClient.getUser(anyLong())).thenReturn(R.ok(null));
    }

    @Test
    void createBook_publishesType1Dynamic() {
        BookSummaryDTO created = bookService.createBook(900010L, 2, "测试新书P2E", null, 1L, null, null);
        assertThat(created).isNotNull();

        ArgumentCaptor<DynamicPublishDTO> captor = ArgumentCaptor.forClass(DynamicPublishDTO.class);
        verify(dynamicClient).publish(captor.capture());
        DynamicPublishDTO dto = captor.getValue();
        assertThat(dto.getDynamicType()).isEqualTo(1);
        assertThat(dto.getRefType()).isEqualTo(1);
        assertThat(dto.getRefId()).isEqualTo(created.getBookId());
        assertThat(dto.getAuthorId()).isEqualTo(900010L);
    }

    @Test
    void updateBook_finish_publishesType2Dynamic() {
        BookSummaryDTO created = bookService.createBook(900011L, 2, "完结测试书", null, 1L, null, null);
        // createBook 已发布 type=1；此处捕获全部 publish，定位 type=2（refId=bookId）
        bookService.updateBook(created.getBookId(), 900011L, 3, null, null, null, null, null, 2);

        ArgumentCaptor<DynamicPublishDTO> captor = ArgumentCaptor.forClass(DynamicPublishDTO.class);
        verify(dynamicClient, atLeast(1)).publish(captor.capture());
        List<DynamicPublishDTO> all = captor.getAllValues();
        boolean hasType2 = all.stream()
                .anyMatch(d -> Integer.valueOf(2).equals(d.getDynamicType())
                        && created.getBookId().equals(d.getRefId()));
        assertThat(hasType2).isTrue();
    }

    @Test
    void publishFailure_doesNotBlockMainFlow() {
        when(dynamicClient.publish(any())).thenThrow(new RuntimeException("social 不可用"));

        BookSummaryDTO created = bookService.createBook(900012L, 2, "降级测试书", null, 1L, null, null);
        assertThat(created).isNotNull();
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book WHERE id = ?", Integer.class, created.getBookId());
        assertThat(cnt).isEqualTo(1);

        BookSummaryDTO finished = bookService.updateBook(created.getBookId(), 900012L, 3, null, null, null, null, null, 2);
        assertThat(finished).isNotNull();
    }
}

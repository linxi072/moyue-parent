package com.moyue.read;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 书架链路集成测试（P0-2 核心靶标）。
 * Testcontainers MySQL + 真实 Flyway V1–V6 建表。
 * 覆盖：加入书架幂等（含逻辑删除行复活）、移出书架（重复移出 20001）、未带头 10002、
 * bookId 为空 10001、阅读进度更新落库。
 * 网关未参与，直接注入 X-User-Id 头模拟网关鉴权透传。
 * <p>三端重构后本测试随阅读域迁入 moyue-reader，包名 {@code com.moyue.read} 保持不变；
 * 应用上下文由唯一启动类 {@code com.moyue.content.ContentApplication} 装配
 * （见 {@code src/test/resources/application-test.yml}）。</p>
 */
@SpringBootTest(classes = com.moyue.content.ContentApplication.class)
@AutoConfigureMockMvc
@Disabled("项目硬性约束：禁用 Docker/Testcontainers（见 docs/不可忽视条件.md）。本用例改造为 H2 或原生 MySQL 集成后再启用。")
@Testcontainers
class BookshelfFlowTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM bookshelf WHERE user_id = 7001");
    }

    @Test
    void addThenRemoveThenReAdd_isIdempotentAndRevivable() throws Exception {
        // 第一次加入
        mockMvc.perform(post("/api/v1/read/bookshelf")
                        .header("X-User-Id", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":3001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // 重复加入 → 幂等，不重复造行
        mockMvc.perform(post("/api/v1/read/bookshelf")
                        .header("X-User-Id", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":3001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(activeRows()).isEqualTo(1);

        // 移出
        mockMvc.perform(delete("/api/v1/read/bookshelf/3001")
                        .header("X-User-Id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(activeRows()).isEqualTo(0);

        // 再次移出 → 资源不存在
        mockMvc.perform(delete("/api/v1/read/bookshelf/3001")
                        .header("X-User-Id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));

        // 重新加入 → 复活逻辑删除行，仍只有一行有效记录
        mockMvc.perform(post("/api/v1/read/bookshelf")
                        .header("X-User-Id", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":3001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(activeRows()).isEqualTo(1);
    }

    @Test
    void addToShelf_requiresUserIdHeader() throws Exception {
        mockMvc.perform(post("/api/v1/read/bookshelf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":3001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void addToShelf_bookIdNull_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/read/bookshelf")
                        .header("X-User-Id", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("书籍 ID 不能为空"));
    }

    @Test
    void updateProgress_persistsLastChapter() throws Exception {
        mockMvc.perform(post("/api/v1/read/bookshelf")
                        .header("X-User-Id", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":3002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/read/bookshelf/3002/progress")
                        .header("X-User-Id", "7001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterId\":4001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer lastChapter = jdbcTemplate.queryForObject(
                "SELECT last_chapter_id FROM bookshelf WHERE user_id = 7001 AND book_id = 3002 AND is_deleted = 0",
                Integer.class);
        assertThat(lastChapter).isEqualTo(4001);
    }

    private Integer activeRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookshelf WHERE user_id = 7001 AND book_id = 3001 AND is_deleted = 0",
                Integer.class);
    }
}

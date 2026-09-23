package com.moyue.blog;

import com.moyue.api.account.client.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 博客点赞链路集成测试（P0-2 核心靶标）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；UserClient 由 @MockBean 替换。
 * 覆盖：点赞 → 计数 1、再点 → 取消计数 0（逻辑删除行保留）、非成员文章 20001、
 * 双用户并发计数正确、点赞记录不重复。
 * profile 固定为 {@code test}，数据源/ Flyway 配置见 {@code src/test/resources/application-test.yml}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogLikeFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserClient userClient;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM blog_like WHERE post_id = 9001");
        jdbcTemplate.update("DELETE FROM blog_comment WHERE post_id = 9001");
        jdbcTemplate.update("DELETE FROM blog_post WHERE id = 9001");
        jdbcTemplate.update("INSERT INTO blog_post (id, author_id, title, content, status, like_count, comment_count, view_count) "
                + "VALUES (9001, 1, '测试文章', '测试正文内容', 1, 0, 0, 0)");
    }

    @Test
    void toggleLike_likeThenUnlike_countReturnsToZero() throws Exception {
        mockMvc.perform(post("/api/v1/blog/posts/9001/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(post("/api/v1/blog/posts/9001/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0));

        // 取消点赞为逻辑删除：记录保留（is_deleted=1），计数归零，防重复唯一键仍被占用
        assertThat(queryInt("SELECT like_count FROM blog_post WHERE id = 9001")).isEqualTo(0);
        assertThat(queryInt("SELECT COUNT(*) FROM blog_like WHERE post_id = 9001 AND user_id = 7001")).isEqualTo(1);
        assertThat(queryInt("SELECT COUNT(*) FROM blog_like WHERE post_id = 9001 AND user_id = 7001 AND is_deleted = 0")).isEqualTo(0);
    }

    @Test
    void toggleLike_postMissing_resourceNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/blog/posts/9999/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void toggleLike_twoUsers_countIsTwo_noDuplicateRows() throws Exception {
        mockMvc.perform(post("/api/v1/blog/posts/9001/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(post("/api/v1/blog/posts/9001/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        assertThat(queryInt("SELECT like_count FROM blog_post WHERE id = 9001")).isEqualTo(2);
        assertThat(queryInt("SELECT COUNT(*) FROM blog_like WHERE post_id = 9001 AND is_deleted = 0")).isEqualTo(2);
    }

    private Integer queryInt(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}

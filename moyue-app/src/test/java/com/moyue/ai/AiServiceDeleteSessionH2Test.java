package com.moyue.ai;

import com.moyue.ai.service.AiService;
import com.moyue.api.search.client.SearchIndexClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * AiService 会话删除 H2 集成测试：验证真实逻辑删除 SQL（会话 + 级联消息 is_deleted=1）、
 * 会话列表对已删会话的过滤、以及 ES 索引清理回调。以 {@code @MockBean SearchIndexClient}
 * 隔离检索服务（非阻断）。建表 / 数据源约定同 {@code PointsServiceRiskHookTest}。
 */
@SpringBootTest
@ActiveProfiles("test")
class AiServiceDeleteSessionH2Test {

    @Autowired
    private AiService aiService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private SearchIndexClient searchIndexClient;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM ai_message WHERE session_id IN (5001, 5002)");
        jdbcTemplate.update("DELETE FROM ai_session WHERE id IN (5001, 5002)");
        jdbcTemplate.update("INSERT INTO ai_session (id, user_id, title) VALUES (5001, 7001, '会话A')");
        jdbcTemplate.update("INSERT INTO ai_session (id, user_id, title) VALUES (5002, 7001, '会话B')");
        jdbcTemplate.update("INSERT INTO ai_message (id, session_id, role, content) VALUES (6001, 5001, 1, 'q1')");
        jdbcTemplate.update("INSERT INTO ai_message (id, session_id, role, content) VALUES (6002, 5001, 2, 'a1')");
        jdbcTemplate.update("INSERT INTO ai_message (id, session_id, role, content) VALUES (6003, 5002, 1, 'q2')");
    }

    @Test
    void deleteSession_logicalDeletesSessionAndMessages_andExcludesFromList() {
        aiService.deleteSession(7001L, 5001L);

        // 会话与消息逻辑删除
        assertThat(queryInt("SELECT is_deleted FROM ai_session WHERE id = 5001")).isEqualTo(1);
        assertThat(queryInt("SELECT COUNT(*) FROM ai_message WHERE session_id = 5001 AND is_deleted = 0")).isEqualTo(0);
        // 同用户其他会话不受影响
        assertThat(queryInt("SELECT is_deleted FROM ai_session WHERE id = 5002")).isEqualTo(0);
        // 会话列表不再包含已删会话
        assertThat(aiService.pageSessions(7001L, 1, 20).getTotal()).isEqualTo(1);
        // ES 索引按会话清理
        verify(searchIndexClient, timeout(2000)).removeQaBySession(5001L);
    }

    @Test
    void clearSessions_logicalDeletesAllUserSessions() {
        long n = aiService.clearSessions(7001L);

        assertThat(n).isEqualTo(2);
        assertThat(queryInt("SELECT COUNT(*) FROM ai_session WHERE user_id = 7001 AND is_deleted = 0")).isEqualTo(0);
        assertThat(queryInt("SELECT COUNT(*) FROM ai_message WHERE session_id IN (5001, 5002) AND is_deleted = 0")).isEqualTo(0);
        verify(searchIndexClient, timeout(2000)).removeQaBySession(5001L);
        verify(searchIndexClient, timeout(2000)).removeQaBySession(5002L);
    }

    private int queryInt(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}

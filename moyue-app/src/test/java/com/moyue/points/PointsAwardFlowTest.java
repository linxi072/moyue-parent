package com.moyue.points;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 积分发放 / 每日签到链路集成测试（P0-3 靶标）。
 *
 * <p>覆盖内部发放端点（{@code POST /api/v1/internal/points/award}，供阅读时长 / 评论奖励等生产者经 Feign 调用）
 * 与每日签到（{@code POST /api/v1/points/check-in}）：
 * 账户懒初始化后正确落库、余额与累计获得原子累加、流水台账逐笔可查、
 * 签到按 {@code uk_user_date} 唯一键做「一人一天一次」的并发去重。
 *
 * <p>H2 内存库（MySQL 兼容模式）+ Flyway 全量建表，无 Mock 数据层。
 * profile 固定 {@code test}，数据源与 Flyway 配置见 {@code src/test/resources/application-test.yml}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointsAwardFlowTest {

    /** 签到固定奖励（与 PointsService.CHECK_IN_POINTS 对齐） */
    private static final int CHECK_IN_POINTS = 10;

    private static final long USER_NO_ACCOUNT = 7100L;
    private static final long USER_INVALID_BIZ = 7101L;
    private static final long USER_BAD_POINTS = 7102L;
    private static final long USER_CHECK_IN = 7103L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        String[] tables = {"points_flow", "points_check_in", "points_account"};
        for (String table : tables) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id BETWEEN ? AND ?",
                    USER_NO_ACCOUNT, USER_CHECK_IN + 1);
        }
    }

    private Integer queryInt(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String awardBody(long userId, Integer bizType, Integer points, String remark) {
        return "{\"userId\":" + userId
                + ",\"bizType\":" + bizType
                + ",\"points\":" + points
                + ",\"remark\":\"" + remark + "\"}";
    }

    // --------------------------------------------------------------- 内部发放端点

    @Test
    @DisplayName("内部发放：账户不存在时懒建户并落库（余额/累计获得原子累加），流水逐笔落台账")
    void award_autoCreatesAccountAndPersistsFlow() throws Exception {
        mockMvc.perform(post("/api/v1/internal/points/award")
                        .header("X-Service-Token", "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody(USER_NO_ACCOUNT, 2, 100, "阅读时长奖励")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(100));

        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(100);
        assertThat(queryInt("SELECT total_earned FROM points_account WHERE user_id = ?", USER_NO_ACCOUNT))
                .isEqualTo(100);
        assertThat(queryInt("SELECT total_spent FROM points_account WHERE user_id = ?", USER_NO_ACCOUNT)).isZero();
        assertThat(queryInt("SELECT COUNT(*) FROM points_flow WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(1);
        assertThat(queryInt("SELECT biz_type FROM points_flow WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(2);
        assertThat(queryInt("SELECT points FROM points_flow WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(100);
    }

    @Test
    @DisplayName("内部发放：多次发放累加余额与累计获得，流水逐笔独立记账")
    void award_multipleTimes_accumulatesBalanceAndFlows() throws Exception {
        mockMvc.perform(post("/api/v1/internal/points/award")
                        .header("X-Service-Token", "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody(USER_NO_ACCOUNT, 2, 30, "阅读时长")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/internal/points/award")
                        .header("X-Service-Token", "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody(USER_NO_ACCOUNT, 3, 20, "评论奖励")))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(50);
        assertThat(queryInt("SELECT total_earned FROM points_account WHERE user_id = ?", USER_NO_ACCOUNT))
                .isEqualTo(50);
        assertThat(queryInt("SELECT COUNT(*) FROM points_flow WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(2);
    }

    @Test
    @DisplayName("内部发放：bizType 越界（0 / 6）返回 10001，不建户不落流水")
    void award_invalidBizType_rejected() throws Exception {
        for (int bizType : new int[]{0, 6}) {
            mockMvc.perform(post("/api/v1/internal/points/award")
                        .header("X-Service-Token", "dev-internal-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(awardBody(USER_INVALID_BIZ, bizType, 10, "非法业务类型")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10001));
        }

        assertThat(queryInt("SELECT COUNT(*) FROM points_account WHERE user_id = ?", USER_INVALID_BIZ)).isZero();
        assertThat(queryInt("SELECT COUNT(*) FROM points_flow WHERE user_id = ?", USER_INVALID_BIZ)).isZero();
    }

    @Test
    @DisplayName("内部发放：积分非正（0 / -5）返回 10001，不建户不落流水")
    void award_nonPositivePoints_rejected() throws Exception {
        for (int points : new int[]{0, -5}) {
            mockMvc.perform(post("/api/v1/internal/points/award")
                        .header("X-Service-Token", "dev-internal-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(awardBody(USER_BAD_POINTS, 4, points, "非法积分")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(10001));
        }

        assertThat(queryInt("SELECT COUNT(*) FROM points_account WHERE user_id = ?", USER_BAD_POINTS)).isZero();
        assertThat(queryInt("SELECT COUNT(*) FROM points_flow WHERE user_id = ?", USER_BAD_POINTS)).isZero();
    }

    // --------------------------------------------------------------- 每日签到

    @Test
    @DisplayName("签到首次：发放固定积分并落签到记录 + 流水，账户懒建户")
    void checkIn_firstTime_awardsOnce() throws Exception {
        mockMvc.perform(post("/api/v1/points/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_CHECK_IN + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(CHECK_IN_POINTS));

        assertThat(queryInt("SELECT COUNT(*) FROM points_check_in WHERE user_id = ?", USER_CHECK_IN)).isEqualTo(1);
        assertThat(queryInt("SELECT points FROM points_check_in WHERE user_id = ?", USER_CHECK_IN))
                .isEqualTo(CHECK_IN_POINTS);
        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = ?", USER_CHECK_IN))
                .isEqualTo(CHECK_IN_POINTS);
        assertThat(queryInt("SELECT biz_type FROM points_flow WHERE user_id = ?", USER_CHECK_IN)).isEqualTo(1);
    }

    @Test
    @DisplayName("签到重复：同日第二次返回 10001「今日已签到」，余额与签到记录不重复增长")
    void checkIn_sameDayTwice_rejectedAndNoDoubleAward() throws Exception {
        mockMvc.perform(post("/api/v1/points/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_CHECK_IN + "}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/points/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_CHECK_IN + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("今日已签到"));

        assertThat(queryInt("SELECT COUNT(*) FROM points_check_in WHERE user_id = ?", USER_CHECK_IN)).isEqualTo(1);
        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = ?", USER_CHECK_IN))
                .isEqualTo(CHECK_IN_POINTS);
        assertThat(queryInt("SELECT COUNT(*) FROM points_flow WHERE user_id = ?", USER_CHECK_IN)).isEqualTo(1);
    }

    @Test
    @DisplayName("签到与发放叠加：余额为各笔之和不丢失（原子加法口径）")
    void checkIn_afterAward_accumulatesBalance() throws Exception {
        mockMvc.perform(post("/api/v1/internal/points/award")
                        .header("X-Service-Token", "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awardBody(USER_CHECK_IN, 4, 90, "系统发放")))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/points/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_CHECK_IN + "}"))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = ?", USER_CHECK_IN))
                .isEqualTo(90 + CHECK_IN_POINTS);
        assertThat(queryInt("SELECT total_earned FROM points_account WHERE user_id = ?", USER_CHECK_IN))
                .isEqualTo(90 + CHECK_IN_POINTS);
        assertThat(queryInt("SELECT COUNT(*) FROM points_flow WHERE user_id = ?", USER_CHECK_IN)).isEqualTo(2);
    }

    @Test
    @DisplayName("账户查询端点：不存在的用户懒建户并返回余额 0（非事务路径，建户落库）")
    void getAccount_missingUser_createsZeroBalanceAccount() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/points/accounts/{userId}", USER_NO_ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(USER_NO_ACCOUNT))
                .andExpect(jsonPath("$.data.balance").value(0));

        assertThat(queryInt("SELECT COUNT(*) FROM points_account WHERE user_id = ?", USER_NO_ACCOUNT)).isEqualTo(1);
    }
}

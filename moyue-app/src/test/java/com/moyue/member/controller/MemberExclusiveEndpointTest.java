package com.moyue.member.controller;

import com.moyue.common.Constants;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会员专属接口端到端拦截测试（P2-B 权益框架深化）。
 * 经真实 HTTP（MockMvc）验证 @RequiresMember 切面：HeaderInterceptor 装配 X-User-Id →
 * SecurityContextHolder → MemberRequiredAspect 校验会员权益 → 拒绝非会员 / 放行会员。
 * 统一响应体：HTTP 200 承载业务码（code）。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemberExclusiveEndpointTest {

    private static final long MEMBER = 7001L;
    private static final long NON_MEMBER = 7002L;
    private static final String TIER = "EXCLUSIVE_BASIC";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberService memberService;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM member_subscription WHERE user_id IN (?, ?)", MEMBER, NON_MEMBER);
        jdbcTemplate.update("DELETE FROM member_tier WHERE tier_code = ?", TIER);
        jdbcTemplate.update("INSERT INTO member_tier "
                        + "(id, tier_code, tier_name, monthly_price, duration_days, ad_free, discount_rate, badge, sort, is_deleted, create_time, update_time) "
                        + "VALUES (7001001, ?, '专属测试包', 15.00, 30, 1, 0.90, 'exclusive', 1, 0, NOW(), NOW())",
                TIER);
        memberService.subscribe(MEMBER, TIER);
    }

    @Test
    @DisplayName("生效中会员访问 /member/exclusive → 200 且返回专属内容")
    void member_access_exclusive_ok() throws Exception {
        mockMvc.perform(get("/api/v1/member/exclusive").header(Constants.USER_ID_HEADER, String.valueOf(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.welcome").exists());
    }

    @Test
    @DisplayName("非会员访问 /member/exclusive → 10003 MEMBER_REQUIRED")
    void nonMember_access_exclusive_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/member/exclusive").header(Constants.USER_ID_HEADER, String.valueOf(NON_MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("未带 X-User-Id 访问 /member/exclusive → 10002 UNAUTHORIZED")
    void anonymous_access_exclusive_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/member/exclusive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("享折扣会员访问 /member/exclusive/discount → 200 且返回折扣率 0.90")
    void memberWithDiscount_access_discount_ok() throws Exception {
        mockMvc.perform(get("/api/v1/member/exclusive/discount").header(Constants.USER_ID_HEADER, String.valueOf(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(0.90));
    }
}

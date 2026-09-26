package com.moyue.member.controller;

import com.moyue.common.Constants;
import com.moyue.member.entity.MemberSubscriptionEntity;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会员中心 / 自动续费开关接口端到端测试（P2-B，H2 + MockMvc）。
 * 经真实 HTTP 验证：会员 GET /member/center 返回聚合视图（activeSubscription 非空）；
 * 非会员返回 activeSubscription=null；匿名返回 10002 UNAUTHORIZED；
 * POST /member/subscriptions/{id}/auto-renew enable=true 回写 renewAt。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MemberCenterTest {

    private static final long MEMBER = 7101L;
    private static final long NON_MEMBER = 7102L;
    private static final long AUTO_USER = 7103L;
    private static final String TIER = "CENTER_TIER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberService memberService;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM member_subscription WHERE user_id IN (?, ?, ?)", MEMBER, NON_MEMBER, AUTO_USER);
        jdbcTemplate.update("DELETE FROM member_tier WHERE tier_code = ?", TIER);
        jdbcTemplate.update(
                "INSERT INTO member_tier (id, tier_code, tier_name, monthly_price, duration_days, ad_free, discount_rate, badge, sort, is_deleted, create_time, update_time) "
                        + "VALUES (7101001, ?, '会员中心测试包', 15.00, 30, 1, 0.90, 'center', 1, 0, NOW(), NOW())",
                TIER);
        memberService.subscribe(MEMBER, TIER);
    }

    @Test
    @DisplayName("会员 GET /member/center → 200 且 activeSubscription 非空")
    void member_center_ok() throws Exception {
        mockMvc.perform(get("/api/v1/member/center").header(Constants.USER_ID_HEADER, String.valueOf(MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeSubscription").exists())
                .andExpect(jsonPath("$.data.member").value(true))
                .andExpect(jsonPath("$.data.currentTierName").value("会员中心测试包"));
    }

    @Test
    @DisplayName("非会员 GET /member/center → 200 且 activeSubscription 为 null")
    void nonMember_center_activeNull() throws Exception {
        mockMvc.perform(get("/api/v1/member/center").header(Constants.USER_ID_HEADER, String.valueOf(NON_MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.activeSubscription").isEmpty())
                .andExpect(jsonPath("$.data.member").value(false));
    }

    @Test
    @DisplayName("匿名 GET /member/center → 10002 UNAUTHORIZED")
    void anonymous_center_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/member/center"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("POST auto-renew enable=true → 200 且该 sub renewAt 被设置")
    void autoRenew_enable_setsRenewAt() throws Exception {
        memberService.subscribe(AUTO_USER, TIER);
        Long subId = memberService.getActiveSubscription(AUTO_USER).getId();

        mockMvc.perform(post("/api/v1/member/subscriptions/" + subId + "/auto-renew")
                        .param("userId", String.valueOf(AUTO_USER))
                        .param("enable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MemberSubscriptionEntity sub = memberService.getActiveSubscription(AUTO_USER);
        assertThat(sub).isNotNull();
        assertThat(sub.getAutoRenew()).isTrue();
        assertThat(sub.getRenewAt()).isNotNull();
        assertThat(sub.getRenewAt().isAfter(LocalDateTime.now())).isTrue();
    }
}

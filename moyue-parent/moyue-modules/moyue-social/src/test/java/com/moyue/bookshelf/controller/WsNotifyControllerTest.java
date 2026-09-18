package com.moyue.bookshelf.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WsNotifyController 集成测试（c，H2 + MockMvc）：验证内部通知端点行为。
 * - 目标用户无在线端时返回 R.ok()（no-op），不报错；
 * - 缺 X-Service-Token 时由 InternalAuthInterceptor 拦截返回 403（P2-I 令牌校验）。
 */
@SpringBootTest(classes = com.moyue.social.SocialApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WsNotifyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String BODY =
            "{\"userId\":9001,\"bookId\":1001,\"action\":\"ADD\",\"eventId\":1,\"timestamp\":1710000000000}";

    @Test
    @DisplayName("内部通知：目标用户无在线端时返回 R.ok()（code=0，no-op）")
    void notify_noOnlineSession_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/internal/ws/bookshelf/notify")
                        .header("X-Service-Token", "dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("内部通知：缺 X-Service-Token 被拦截返回 403")
    void notify_withoutToken_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/internal/ws/bookshelf/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }
}

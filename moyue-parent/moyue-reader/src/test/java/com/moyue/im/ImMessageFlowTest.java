package com.moyue.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.api.client.UserClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IM 收发链路集成测试（P0-2 核心靶标 + 孤儿消息缺陷回归）。
 * RANDOM_PORT 真实 Servlet 环境（兼容 WebSocket 基础设施）+ Testcontainers MySQL。
 * 覆盖：创建单聊 → 成员发消息 → 会话预览更新 → 消息可拉取；
 * 回归：对不存在会话发消息必须 20001（修复前为孤儿消息落库 200）；
 * 非会话成员发消息必须 10003。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ImMessageFlowTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserClient userClient;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_message");
        jdbcTemplate.update("DELETE FROM chat_conversation_member");
        jdbcTemplate.update("DELETE FROM chat_conversation");
    }

    @Test
    void sendMessage_flow_updatesPreviewAndListable() throws Exception {
        long convId = createSingleConversation(7001L, 7002L);

        ResponseEntity<String> sendResp = restTemplate.postForEntity(
                "/api/v1/im/conversations/" + convId + "/messages",
                jsonBody("{\"senderId\":7001,\"content\":\"hello moyue\"}"),
                String.class);
        JsonNode send = objectMapper.readTree(sendResp.getBody());
        assertThat(send.get("code").asInt()).isZero();

        // 会话最近消息预览与落库校验
        String lastMessage = jdbcTemplate.queryForObject(
                "SELECT last_message FROM chat_conversation WHERE id = " + convId, String.class);
        assertThat(lastMessage).isEqualTo("hello moyue");
        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE conversation_id = " + convId, Integer.class);
        assertThat(msgCount).isEqualTo(1);

        // 消息列表可拉取
        ResponseEntity<String> listResp = restTemplate.getForEntity(
                "/api/v1/im/conversations/" + convId + "/messages", String.class);
        assertThat(listResp.getBody()).contains("hello moyue");
    }

    @Test
    void sendMessage_toMissingConversation_rejectedWith20001() throws Exception {
        long convId = createSingleConversation(7001L, 7002L);
        long missingId = convId + 987654321L;

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/im/conversations/" + missingId + "/messages",
                jsonBody("{\"senderId\":7001,\"content\":\"orphan?\"}"),
                String.class);
        JsonNode body = objectMapper.readTree(resp.getBody());
        assertThat(body.get("code").asInt()).isEqualTo(20001);

        // 不得产生孤儿消息
        Integer orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE conversation_id = " + missingId, Integer.class);
        assertThat(orphans).isZero();
    }

    @Test
    void sendMessage_byNonMember_rejectedWith10003() throws Exception {
        long convId = createSingleConversation(7001L, 7002L);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/im/conversations/" + convId + "/messages",
                jsonBody("{\"senderId\":7003,\"content\":\"intruder\"}"),
                String.class);
        JsonNode body = objectMapper.readTree(resp.getBody());
        assertThat(body.get("code").asInt()).isEqualTo(10003);

        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE conversation_id = " + convId, Integer.class);
        assertThat(msgCount).isZero();
    }

    @Test
    void sendMessage_emptyContent_rejected() throws Exception {
        long convId = createSingleConversation(7001L, 7002L);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/im/conversations/" + convId + "/messages",
                jsonBody("{\"senderId\":7001,\"content\":\"\"}"),
                String.class);
        JsonNode body = objectMapper.readTree(resp.getBody());
        assertThat(body.get("code").asInt()).isEqualTo(10001);
    }

    /** 创建单聊会话并返回其 ID */
    private long createSingleConversation(long ownerId, long otherId) throws Exception {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/im/conversations",
                jsonBody("{\"type\":1,\"ownerId\":" + ownerId + ",\"memberIds\":[" + otherId + "]}"),
                String.class);
        JsonNode body = objectMapper.readTree(resp.getBody());
        assertThat(body.get("code").asInt()).isZero();
        return body.get("data").get("id").asLong();
    }

    private HttpEntity<String> jsonBody(String raw) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(raw, headers);
    }
}

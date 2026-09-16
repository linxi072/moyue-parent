package com.moyue.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.ai.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 大模型网关客户端（OpenAI 兼容 chat/completions）。
 *
 * <p>标准库 {@code java.net.http.HttpClient} 真实外呼（POST JSON + Bearer 鉴权），
 * 不依赖 spring-web 额外组件；任何失败统一抛 RuntimeException，由 {@code LlmReplyEngine}
 * 捕获并降级到关键字引擎（绝不向上抛出）。密钥仅用于鉴权头，绝不打印日志。</p>
 */
@Slf4j
@Component
public class LlmGatewayClient {

    private final LlmProperties props;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public LlmGatewayClient(LlmProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    /**
     * 调用对话补全接口。
     *
     * @param messages OpenAI 格式 messages（role/content）
     * @return 助手文本（已 trim）
     * @throws RuntimeException 序列化失败 / 网关非 2xx / 响应缺 choices / 内容为空
     */
    public String chat(List<Map<String, Object>> messages) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", messages,
                "temperature", 0.3);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new RuntimeException("序列化 LLM 请求体失败：" + ex.getMessage(), ex);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getEndpoint()))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + (props.getApiKey() == null ? "" : props.getApiKey()))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("LLM 网关返回非成功状态：" + status);
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("LLM 响应缺少 choices");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content == null || content.isNull()) {
                throw new RuntimeException("LLM 响应缺少 message.content");
            }
            String text = content.asText();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("LLM 返回空内容");
            }
            return text.trim();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException("LLM 网关调用失败：" + ex.getMessage(), ex);
        }
    }
}

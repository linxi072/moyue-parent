package com.moyue.system.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.system.config.XxlJobAdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * XXL-Job Admin OpenAPI 客户端。
 *
 * <p>Admin 的 OpenAPI 需先登录拿 cookie，故用带 session 的 RestTemplate 维持会话。
 * Admin 不可用时统一抛 {@code SERVICE_DEGRADED}，前端据此改为只读展示。
 *
 * @author moyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XxlJobAdminClient {

    private final XxlJobAdminProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile RestTemplate restTemplate;
    private volatile boolean loggedIn = false;

    private RestTemplate template() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    restTemplate = new RestTemplateBuilder()
                            .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                            .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
                            .build();
                }
            }
        }
        return restTemplate;
    }

    /**
     * 连通性检查。
     *
     * @return 是否可达
     */
    public boolean health() {
        try {
            JsonNode node = post("/jobinfo/pageList", Map.of("start", "0", "length", "1"));
            return node != null;
        } catch (Exception e) {
            log.warn("XXL-Job Admin 不可达：{}", e.getMessage());
            return false;
        }
    }

    /**
     * POST 表单调用 Admin OpenAPI。
     *
     * @param path   接口路径
     * @param params 表单参数
     * @return 响应 JSON 节点
     */
    public JsonNode post(String path, Map<String, String> params) {
        ensureLogin();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach((k, v) -> form.add(k, v == null ? "" : v));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<String> response = template().exchange(
                    properties.getAddress() + path,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    String.class);
            return parse(response.getBody());
        } catch (RestClientException e) {
            log.warn("XXL-Job Admin 调用失败 path={}：{}", path, e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_DEGRADED, "调度中心不可用，定时任务已降级为只读");
        }
    }

    private void ensureLogin() {
        if (loggedIn || !properties.isEnabled()) {
            return;
        }
        synchronized (this) {
            if (loggedIn) {
                return;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("userName", properties.getUsername());
            form.add("password", properties.getPassword());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            try {
                template().postForEntity(properties.getAddress() + "/login",
                        new HttpEntity<>(form, headers), String.class);
                loggedIn = true;
            } catch (RestClientException e) {
                throw new BusinessException(ErrorCode.SERVICE_DEGRADED, "调度中心登录失败");
            }
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("XXL-Job Admin 响应解析失败：{}", e.getMessage());
            return null;
        }
    }
}

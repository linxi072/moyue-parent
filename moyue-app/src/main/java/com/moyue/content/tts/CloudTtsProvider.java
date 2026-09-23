package com.moyue.content.tts;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 云端 TTS Provider（默认实现）。
 *
 * <p>配置驱动优雅降级：当 {@code moyue.tts.secret} / {@code moyue.tts.endpoint} 任一为空时，
 * 视为未接入 TTS，合成直接抛 {@link TtsUnavailableException} → 上层返回 {@code SERVICE_DEGRADED}，
 * 不阻断阅读主流程（对齐 RiskClient / SearchIndexClient 既有降级契约）。</p>
 *
 * <p>厂商中立调用契约：向 {@code endpoint} POST JSON {@code {text, voice, speed}}，
 * 请求头携带 {@code Authorization: Bearer <secret>}，期望响应体为 audio/* 字节。
 * 接入具体厂商（腾讯云/讯飞/微软）时只需在 {@code endpoint} 指向其网关并在网关侧完成签名/鉴权，
 * 业务侧无需改动。</p>
 */
@Service
public class CloudTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(CloudTtsProvider.class);

    @Value("${moyue.tts.secret:}")
    private String secret;

    @Value("${moyue.tts.endpoint:}")
    private String endpoint;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 配置健全性自检日志（不抛错，缺配置仅告警，由合成时降级） */
    @PostConstruct
    public void checkConfig() {
        if (secret.isBlank() || endpoint.isBlank()) {
            log.warn("[tts] 云端 TTS 未配置（secret/endpoint 为空），朗读将降级为 SERVICE_DEGRADED；"
                    + " 生产接入请提供 moyue.tts.secret 与 moyue.tts.endpoint");
        }
    }

    @Override
    public byte[] synthesize(String text, String voice, double speed) throws TtsUnavailableException {
        if (secret.isBlank() || endpoint.isBlank()) {
            throw new TtsUnavailableException("TTS 未配置（缺 secret/endpoint），降级");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + secret);
            Map<String, Object> body = new HashMap<>(4);
            body.put("text", text);
            body.put("voice", voice == null ? "" : voice);
            body.put("speed", speed);
            ResponseEntity<byte[]> resp = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), byte[].class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().length > 0) {
                return resp.getBody();
            }
            throw new TtsUnavailableException("TTS 返回异常：" + resp.getStatusCode());
        } catch (TtsUnavailableException e) {
            throw e;
        } catch (BizException e) {
            throw new TtsUnavailableException("TTS 调用被业务拦截：" + e.getMessage());
        } catch (Exception e) {
            throw new TtsUnavailableException("TTS 调用失败：" + e.getMessage());
        }
    }
}

package com.moyue.message.channel;

import com.moyue.api.account.client.UserClient;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.common.R;
import com.moyue.message.config.PushProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 推送渠道（P1-2 真实接入 + 优雅降级）。
 *
 * <p>配置驱动：{@code moyue.push.enabled=true} 且 {@code endpoint} 非空时，经标准库
 * {@code java.net.http.HttpClient} POST JSON 到推送网关（FCM / APNs / 厂商通道，Bearer 鉴权）。
 * 设备令牌优先级：{@code ChannelMessage.target}（即 {@code MessageDispatchDTO.targetDeviceToken}）
 * → 经 {@link UserClient} 解析用户 {@code deviceToken} → 均无则 {@code pending}（不投、不失败）。
 * 网关不可达 / 未启用时返回 {@code pending}/{@code failure}，<b>绝不抛异常</b>。</p>
 */
@Slf4j
@Component
public class PushChannelSender implements ChannelSender {

    private final PushProperties pushProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    @Autowired(required = false)
    private UserClient userClient;

    @Autowired
    public PushChannelSender(PushProperties pushProperties) {
        this.pushProperties = pushProperties;
    }

    @Override
    public MessageChannel channel() {
        return MessageChannel.PUSH;
    }

    @Override
    public ChannelSendResult send(ChannelMessage msg) {
        if (!pushProperties.isEnabled() || isBlank(pushProperties.getEndpoint())) {
            return ChannelSendResult.pending("推送渠道未启用（配置 moyue.push.enabled=true 接入网关）");
        }
        String token = msg.getTarget();
        if (isBlank(token)) {
            token = resolveDeviceToken(msg.getUserId());
        }
        if (isBlank(token)) {
            return ChannelSendResult.pending("推送渠道无设备令牌，跳过");
        }
        String body = buildPayload(token, msg);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pushProperties.getEndpoint()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + nullToEmpty(pushProperties.getServerKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                return ChannelSendResult.success("推送已下发至设备");
            }
            log.warn("推送网关返回非成功状态 userId={}, status={}", msg.getUserId(), status);
            return ChannelSendResult.failure("推送网关返回非成功状态：" + status);
        } catch (Exception ex) {
            log.warn("推送网关调用失败 userId={}, err={}", msg.getUserId(), ex.getMessage());
            return ChannelSendResult.failure("推送网关调用失败：" + ex.getMessage());
        }
    }

    private String buildPayload(String token, ChannelMessage msg) {
        return "{\"token\":\"" + escape(token) + "\""
                + ",\"platform\":\"" + escape(nullToEmpty(pushProperties.getPlatform())) + "\""
                + ",\"title\":\"" + escape(nullToEmpty(msg.getTitle())) + "\""
                + ",\"body\":\"" + escape(nullToEmpty(msg.getContent())) + "\"}";
    }

    /**
     * 经用户域解析设备令牌（推送渠道缺省令牌时回退）。
     * 用户域不可用时返回 {@code null} → 上层走 pending；密钥/令牌绝不打印日志。
     */
    private String resolveDeviceToken(Long userId) {
        if (userClient == null || userId == null) {
            return null;
        }
        try {
            UserDTO user = userClient.getUser(userId).getData();
            return (user != null) ? user.getDeviceToken() : null;
        } catch (Exception ex) {
            log.warn("解析用户设备令牌失败 userId={}, err={}", userId, ex.getMessage());
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

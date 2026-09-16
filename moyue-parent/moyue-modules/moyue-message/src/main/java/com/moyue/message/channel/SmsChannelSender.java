package com.moyue.message.channel;

import com.moyue.message.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 短信渠道（P1-2 真实接入 + 优雅降级）。
 *
 * <p>配置驱动：{@code moyue.sms.enabled=true} 且 {@code endpoint} 非空时，经标准库
 * {@code java.net.http.HttpClient} POST JSON 到供应商网关（Bearer 鉴权），真实外呼；
 * 未启用 / 网关不可达 / 无收件号时返回 {@code pending}/{@code failure}，<b>绝不抛异常</b>，
 * 逐渠道隔离由 {@link MessageDispatcher} 保证。无密钥即降级，业务代码零改动。</p>
 */
@Slf4j
@Component
public class SmsChannelSender implements ChannelSender {

    private final SmsProperties smsProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    @Autowired
    public SmsChannelSender(SmsProperties smsProperties) {
        this.smsProperties = smsProperties;
    }

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public ChannelSendResult send(ChannelMessage msg) {
        if (!smsProperties.isEnabled() || isBlank(smsProperties.getEndpoint())) {
            return ChannelSendResult.pending("短信渠道未启用（配置 moyue.sms.enabled=true 接入供应商）");
        }
        String phone = msg.getTarget();
        if (isBlank(phone)) {
            return ChannelSendResult.pending("短信渠道无收件手机号，跳过");
        }
        String body = buildPayload(phone, msg);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(smsProperties.getEndpoint()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + nullToEmpty(smsProperties.getAccessKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                return ChannelSendResult.success("短信已发送至 " + mask(phone));
            }
            log.warn("短信网关返回非成功状态 userId={}, status={}", msg.getUserId(), status);
            return ChannelSendResult.failure("短信网关返回非成功状态：" + status);
        } catch (Exception ex) {
            log.warn("短信网关调用失败 userId={}, err={}", msg.getUserId(), ex.getMessage());
            return ChannelSendResult.failure("短信网关调用失败：" + ex.getMessage());
        }
    }

    private String buildPayload(String phone, ChannelMessage msg) {
        return "{\"to\":\"" + escape(phone) + "\""
                + ",\"code\":\"" + escape(nullToEmpty(msg.getTemplateCode())) + "\""
                + ",\"signName\":\"" + escape(nullToEmpty(smsProperties.getSignName())) + "\""
                + ",\"templateId\":\"" + escape(nullToEmpty(smsProperties.getTemplateId())) + "\""
                + ",\"title\":\"" + escape(nullToEmpty(msg.getTitle())) + "\""
                + ",\"content\":\"" + escape(nullToEmpty(msg.getContent())) + "\"}";
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

    private static String mask(String phone) {
        return phone.length() > 4
                ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4)
                : phone;
    }
}

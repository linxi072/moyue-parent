package com.moyue.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推送渠道配置（前缀 {@code moyue.push}）。
 *
 * <p>默认 {@code enabled=false}：未配置网关时 {@link PushChannelSender} 返回
 * {@code pending}（优雅降级）；配置 {@code moyue.push.enabled=true} + {@code endpoint} +
 * {@code serverKey} 后真实外呼（FCM / APNs / 厂商通道等）。</p>
 *
 * <p>网关协议为「POST JSON + Bearer 鉴权」的通用形态（{@code token/title/body}），
 * {@code platform} 仅作标识（FCM/APNS/HUAWEI/...），具体报文差异可在发送器内按 platform 适配。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.push")
public class PushProperties {

    /** 是否启用真实外呼（默认 false：降级为 pending） */
    private boolean enabled = false;

    /** 推送网关地址（POST JSON） */
    private String endpoint;

    /** 网关鉴权密钥（Bearer / Authorization 头，绝不打印日志） */
    private String serverKey;

    /** 平台标识：FCM / APNS / HUAWEI / XIAOMI / ...（可选） */
    private String platform;
}

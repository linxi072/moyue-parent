package com.moyue.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信渠道配置（前缀 {@code moyue.sms}）。
 *
 * <p>默认 {@code enabled=false}：未配置供应商时 {@link SmsChannelSender} 返回
 * {@code pending}（优雅降级，不抛异常、不阻塞主流程）；运维在 yml / Nacos 配置
 * {@code moyue.sms.enabled=true} + {@code endpoint} + 鉴权信息后即可真实外呼，业务代码零改动。</p>
 *
 * <p>网关协议为「POST JSON + Bearer 鉴权」的通用形态（{@code to/code/content/signName/templateId}），
 * 适配阿里云/腾讯云等主流短信网关；若供应商协议差异较大，可在 {@link SmsChannelSender} 内按
 * {@code accessKey} / {@code secret} 派生签名，此处仅承载可配置参数、不硬编码任何密钥。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.sms")
public class SmsProperties {

    /** 是否启用真实外呼（默认 false：降级为 pending） */
    private boolean enabled = false;

    /** 供应商网关地址（POST JSON） */
    private String endpoint;

    /** 鉴权 AccessKey / AppId（Bearer 头或签名派生源） */
    private String accessKey;

    /** 鉴权 Secret（用于签名派生，绝不打印日志） */
    private String secret;

    /** 短信签名 */
    private String signName;

    /** 默认模板 ID（DTO 可覆盖） */
    private String templateId;
}

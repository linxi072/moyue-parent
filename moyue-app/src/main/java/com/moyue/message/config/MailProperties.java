package com.moyue.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件渠道配置（前缀 {@code moyue.mail}）。
 *
 * <p>SMTP 连接参数全部可配、无硬编码：dev 默认指向 MailHog（{@code localhost:1025}，无认证、无 TLS），
 * 生产由运维经环境变量注入。JavaMailSender 的实际连接参数由 {@code spring.mail.*} 提供
 * （二者在 application.yml 中指向同一组 {@code MOYUE_MAIL_*} 环境变量）；本类额外承载
 * 发件人地址等业务参数。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.mail")
public class MailProperties {

    /** SMTP 主机（镜像 spring.mail.host） */
    private String host;

    /** SMTP 端口（镜像 spring.mail.port） */
    private int port;

    /** SMTP 用户名（镜像 spring.mail.username） */
    private String username;

    /** SMTP 口令（镜像 spring.mail.password） */
    private String password;

    /** 默认发件人地址 */
    private String from;
}

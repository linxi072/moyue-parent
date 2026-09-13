package com.moyue.message.channel;

import com.moyue.api.client.UserClient;
import com.moyue.api.dto.UserDTO;
import com.moyue.message.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件渠道（真实现，P2-14）：经 JavaMailSender（SMTP）发送，SMTP 参数全走配置、无硬编码。
 *
 * <p>收件人优先级：{@code ChannelMessage.target}（即 {@code MessageDispatchDTO.targetEmail}）→
 * 经 {@link UserClient} 解析用户邮箱 → 均无则返回 {@code pending=true}（不算失败）。
 * 发送异常一律 try/catch 转为 {@code success=false}，不向上抛出。</p>
 */
@Slf4j
@Component
public class EmailChannelSender implements ChannelSender {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired(required = false)
    private UserClient userClient;

    @Autowired
    private MailProperties mailProperties;

    @Override
    public MessageChannel channel() {
        return MessageChannel.EMAIL;
    }

    @Override
    public ChannelSendResult send(ChannelMessage msg) {
        String to = msg.getTarget();
        if (isBlank(to)) {
            to = resolveEmail(msg.getUserId());
        }
        if (isBlank(to)) {
            return ChannelSendResult.pending("未提供收件邮箱且用户资料无邮箱，邮件渠道跳过");
        }
        if (mailSender == null) {
            return ChannelSendResult.failure("JavaMailSender 未就绪（spring.mail 未配置）");
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            if (!isBlank(mailProperties.getFrom())) {
                mail.setFrom(mailProperties.getFrom());
            }
            mail.setTo(to);
            mail.setSubject(msg.getTitle() == null ? "" : msg.getTitle());
            mail.setText(msg.getContent() == null ? "" : msg.getContent());
            mailSender.send(mail);
            return ChannelSendResult.success("邮件已发送至 " + to);
        } catch (Exception ex) {
            // 发送失败不抛出：转为失败结果，由分发器记录并继续其它渠道
            log.warn("邮件渠道发送失败 userId={}, to={}, err={}", msg.getUserId(), to, ex.getMessage());
            return ChannelSendResult.failure("邮件发送失败：" + ex.getMessage());
        }
    }

    /**
     * 经用户域解析邮箱。
     * <p>注：当前 {@link UserDTO} 无 email 字段（架构设计 §十一-5）：保留解析入口，
     * 缺失时返回 {@code null} → 上层走 pending；用户域补充邮箱字段后可在此返回 {@code u.getEmail()}。</p>
     */
    private String resolveEmail(Long userId) {
        if (userClient == null || userId == null) {
            return null;
        }
        try {
            UserDTO user = userClient.getUser(userId).getData();
            if (user != null) {
                log.debug("用户 {} 资料暂无可解析邮箱字段，邮件渠道回退 pending", userId);
            }
            return null;
        } catch (Exception ex) {
            log.warn("解析用户邮箱失败 userId={}, err={}", userId, ex.getMessage());
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

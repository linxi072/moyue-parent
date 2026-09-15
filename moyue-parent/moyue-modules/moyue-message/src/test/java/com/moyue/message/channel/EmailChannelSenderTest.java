package com.moyue.message.channel;

import com.moyue.api.account.client.UserClient;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.common.R;
import com.moyue.message.config.MailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailChannelSender 邮件渠道回归测试（P2-14）。
 *
 * <p>纯 Mockito 单测：JavaMailSender / UserClient 以 Mock 注入，覆盖「有 target 直发」、
 * 「无 target 走用户域解析的降级路径」、「SMTP 未就绪 / 发送异常转 failure 且不抛异常」。</p>
 *
 * <p>当前契约说明（P1-2）：{@code moyue-api-account} 的 {@link UserDTO} 目前<b>没有</b> email 字段，
 * 故 {@code EmailChannelSender.resolveEmail} 恒返回 {@code null}，无 target 时最终走
 * {@code pending}。待 P1-2 补齐 {@code UserDTO.email} 后，本类用例需升级为断言解析出真实邮箱并成功发送。</p>
 */
class EmailChannelSenderTest {

    private static final long USER_ID = 7L;
    private static final String FROM = "noreply@moyue.com";

    private final MailProperties mailProperties = new MailProperties();

    private JavaMailSender mailSender;
    private UserClient userClient;

    @BeforeEach
    void setUp() {
        mailProperties.setFrom(FROM);
        mailSender = mock(JavaMailSender.class);
        userClient = mock(UserClient.class);
    }

    private EmailChannelSender newSender(JavaMailSender mailSender, UserClient userClient) {
        EmailChannelSender sender = new EmailChannelSender();
        ReflectionTestUtils.setField(sender, "mailSender", mailSender);
        ReflectionTestUtils.setField(sender, "userClient", userClient);
        ReflectionTestUtils.setField(sender, "mailProperties", mailProperties);
        return sender;
    }

    private ChannelMessage message(String target) {
        ChannelMessage msg = new ChannelMessage();
        msg.setUserId(USER_ID);
        msg.setChannel(MessageChannel.EMAIL);
        msg.setTitle("审核结果");
        msg.setContent("您的作品已通过审核");
        msg.setTarget(target);
        return msg;
    }

    @Test
    @DisplayName("channel() 恒返回 EMAIL（SPI 路由契约）")
    void channelShouldReturnEmail() {
        assertThat(newSender(mailSender, userClient).channel()).isEqualTo(MessageChannel.EMAIL);
    }

    @Test
    @DisplayName("提供 target：经 SMTP 真实发送，回填 from/to/subject/text，返回 success")
    void sendShouldDeliverViaSmtpWhenTargetProvided() {
        EmailChannelSender sender = newSender(mailSender, userClient);

        ChannelSendResult result = sender.send(message("reader@moyue.com"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isPending()).isFalse();
        assertThat(result.getDetail()).contains("reader@moyue.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertThat(mail.getFrom()).isEqualTo(FROM);
        assertThat(mail.getTo()).containsExactly("reader@moyue.com");
        assertThat(mail.getSubject()).isEqualTo("审核结果");
        assertThat(mail.getText()).isEqualTo("您的作品已通过审核");
    }

    @Test
    @DisplayName("无 target 且用户资料无邮箱字段：回退 pending（当前契约，P1-2 后升级），且不调用 SMTP")
    void sendShouldFallbackToPendingWhenUserProfileHasNoEmailField() {
        UserDTO user = new UserDTO();
        user.setId(USER_ID);
        user.setPhone("13800000000");
        when(userClient.getUser(USER_ID)).thenReturn(R.ok(user));
        EmailChannelSender sender = newSender(mailSender, userClient);

        ChannelSendResult result = sender.send(message(null));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isPending()).isTrue();
        assertThat(result.getDetail()).contains("邮件渠道跳过");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("用户域客户端不可用（未装配）：降级 pending，不抛异常、不外呼")
    void sendShouldFallbackToPendingWhenUserClientAbsent() {
        EmailChannelSender sender = newSender(mailSender, null);

        ChannelSendResult result = sender.send(message(null));

        assertThat(result.isPending()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("用户域解析异常：降级 pending 且整体不抛异常（异常被吞并转 warn）")
    void sendShouldNotThrowWhenResolvingUserFails() {
        when(userClient.getUser(USER_ID)).thenThrow(new RuntimeException("feign timeout"));
        EmailChannelSender sender = newSender(mailSender, userClient);

        ChannelSendResult result = sender.send(message(null));

        assertThat(result.isPending()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("target 为纯空白：等同缺失，降级 pending，不外呼")
    void sendShouldTreatBlankTargetAsAbsent() {
        EmailChannelSender sender = newSender(mailSender, userClient);

        ChannelSendResult result = sender.send(message("   "));

        assertThat(result.isPending()).isTrue();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("提供 target 但 JavaMailSender 未就绪：返回 failure（不抛异常）")
    void sendShouldReturnFailureWhenMailSenderNotReady() {
        EmailChannelSender sender = newSender(null, userClient);

        ChannelSendResult result = sender.send(message("reader@moyue.com"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isPending()).isFalse();
        assertThat(result.getDetail()).contains("JavaMailSender 未就绪");
    }

    @Test
    @DisplayName("SMTP 发送抛异常：转为 failure 且不向调用方抛出")
    void sendShouldReturnFailureWhenSmtpThrows() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        EmailChannelSender sender = newSender(mailSender, userClient);

        ChannelSendResult result = sender.send(message("reader@moyue.com"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDetail()).contains("邮件发送失败");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}

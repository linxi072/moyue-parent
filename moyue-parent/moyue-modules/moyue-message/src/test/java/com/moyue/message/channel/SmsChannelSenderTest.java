package com.moyue.message.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SmsChannelSender 短信渠道回归测试（P2-14 桩实现钉现状）。
 *
 * <p>当前为桩实现：仅打印结构化日志并返回 {@code pending}，<b>不做任何真实外呼</b>、绝不抛异常。
 * 用例把该现状钉住，防止后续误改语义；<b>P1-2 接入真实网关后</b>需升级为断言真实投递结果。</p>
 */
class SmsChannelSenderTest {

    private final SmsChannelSender sender = new SmsChannelSender();

    private ChannelMessage message() {
        ChannelMessage msg = new ChannelMessage();
        msg.setUserId(7L);
        msg.setChannel(MessageChannel.SMS);
        msg.setTarget("13800000000");
        msg.setTemplateCode("AUDIT_PASS");
        msg.setBizType("AUDIT");
        msg.setBizId(99L);
        return msg;
    }

    @Test
    @DisplayName("channel() 恒返回 SMS（SPI 路由契约）")
    void channelShouldReturnSms() {
        assertThat(sender.channel()).isEqualTo(MessageChannel.SMS);
    }

    @Test
    @DisplayName("桩实现：返回 pending（非 success / 非 failure）且不抛异常、不真实外呼")
    void sendShouldReturnPendingStub() {
        ChannelSendResult result = sender.send(message());

        assertThat(result.isPending()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDetail()).contains("未接入供应商");
        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();
    }
}

package com.moyue.message.channel;

import com.moyue.message.config.PushProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * PushChannelSender 推送渠道测试（P1-2 真实接入 + 优雅降级）。
 *
 * <p>默认配置 {@code enabled=false} 下返回 {@code pending}（不抛异常、不真实外呼）；
 * 用例把「未启用即降级 pending」这一契约钉住。启用真实网关（yml / Nacos 配
 * {@code moyue.push.enabled=true} + {@code endpoint}）后的投递断言在集成环境补齐。</p>
 */
class PushChannelSenderTest {

    private final PushChannelSender sender = new PushChannelSender(new PushProperties());

    private ChannelMessage message() {
        ChannelMessage msg = new ChannelMessage();
        msg.setUserId(7L);
        msg.setChannel(MessageChannel.PUSH);
        msg.setTemplateCode("AUDIT_PASS");
        msg.setBizType("AUDIT");
        msg.setBizId(99L);
        return msg;
    }

    @Test
    @DisplayName("channel() 恒返回 PUSH（SPI 路由契约）")
    void channelShouldReturnPush() {
        assertThat(sender.channel()).isEqualTo(MessageChannel.PUSH);
    }

    @Test
    @DisplayName("未启用：返回 pending（非 success / 非 failure）且不抛异常、不真实外呼")
    void sendShouldReturnPendingWhenDisabled() {
        ChannelSendResult result = sender.send(message());

        assertThat(result.isPending()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDetail()).contains("推送渠道未启用");
        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();
    }
}

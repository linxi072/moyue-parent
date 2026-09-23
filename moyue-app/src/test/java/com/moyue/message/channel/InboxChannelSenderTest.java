package com.moyue.message.channel;

import com.moyue.message.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * InboxChannelSender 站内信渠道回归测试（P2-14 真实现）。
 *
 * <p>站内信为真实现：委托 {@link MessageService#sendInbox} 落 notice 表并返回 {@code success}。</p>
 */
class InboxChannelSenderTest {

    private final MessageService messageService = mock(MessageService.class);
    private final InboxChannelSender sender = createSender();

    private InboxChannelSender createSender() {
        InboxChannelSender s = new InboxChannelSender();
        ReflectionTestUtils.setField(s, "messageService", messageService);
        return s;
    }

    @Test
    @DisplayName("channel() 恒返回 INBOX（SPI 路由契约）")
    void channelShouldReturnInbox() {
        assertThat(sender.channel()).isEqualTo(MessageChannel.INBOX);
    }

    @Test
    @DisplayName("发送站内信：委托 MessageService.sendInbox(userId,title,content) 并返回 success")
    void sendShouldPersistInboxAndReturnSuccess() {
        ChannelMessage msg = new ChannelMessage();
        msg.setUserId(7L);
        msg.setChannel(MessageChannel.INBOX);
        msg.setTitle("审核结果");
        msg.setContent("您的作品已通过审核");

        ChannelSendResult result = sender.send(msg);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isPending()).isFalse();
        verify(messageService).sendInbox(7L, "审核结果", "您的作品已通过审核");
    }
}

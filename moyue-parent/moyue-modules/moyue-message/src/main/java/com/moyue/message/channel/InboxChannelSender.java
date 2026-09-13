package com.moyue.message.channel;

import com.moyue.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 站内信渠道（真实现，P2-14）：复用 {@link MessageService} 的站内信写入能力，落 notice 表。
 *
 * <p>同时承载 {@code message_channel_record} 记录由分发器统一写入（本类只负责投递）。</p>
 */
@Component
public class InboxChannelSender implements ChannelSender {

    @Autowired
    private MessageService messageService;

    @Override
    public MessageChannel channel() {
        return MessageChannel.INBOX;
    }

    /** 写 notice 表（title / content / userId），返回成功 */
    @Override
    public ChannelSendResult send(ChannelMessage msg) {
        messageService.sendInbox(msg.getUserId(), msg.getTitle(), msg.getContent());
        return ChannelSendResult.success("站内信已入库 userId=" + msg.getUserId());
    }
}

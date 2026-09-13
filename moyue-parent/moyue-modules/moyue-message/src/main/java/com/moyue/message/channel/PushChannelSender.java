package com.moyue.message.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 推送渠道（桩实现，P2-14）。
 *
 * <p>未接入供应商：返回 {@link ChannelSendResult#pending(String)} 并打印结构化日志，
 * <b>绝不抛异常</b>；接入真实推送（APNs / FCM / 厂商通道）时替换本类实现即可，业务代码零改动。</p>
 */
@Slf4j
@Component
public class PushChannelSender implements ChannelSender {

    @Override
    public MessageChannel channel() {
        return MessageChannel.PUSH;
    }

    @Override
    public ChannelSendResult send(ChannelMessage msg) {
        log.warn("[未接入供应商] 推送渠道桩实现：userId={}, templateCode={}, bizType={}, bizId={}",
                msg.getUserId(), msg.getTemplateCode(), msg.getBizType(), msg.getBizId());
        return ChannelSendResult.pending("推送渠道未接入供应商（桩实现）");
    }
}

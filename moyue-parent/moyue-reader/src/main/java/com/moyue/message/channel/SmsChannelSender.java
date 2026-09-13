package com.moyue.message.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信渠道（桩实现，P2-14）。
 *
 * <p>未接入供应商：返回 {@link ChannelSendResult#pending(String)} 并打印结构化日志，
 * <b>绝不抛异常</b>；接入真实供应商时替换本类实现即可，业务代码零改动。</p>
 */
@Slf4j
@Component
public class SmsChannelSender implements ChannelSender {

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public ChannelSendResult send(ChannelMessage msg) {
        log.warn("[未接入供应商] 短信渠道桩实现：userId={}, target={}, templateCode={}, bizType={}, bizId={}",
                msg.getUserId(), msg.getTarget(), msg.getTemplateCode(), msg.getBizType(), msg.getBizId());
        return ChannelSendResult.pending("短信渠道未接入供应商（桩实现）");
    }
}

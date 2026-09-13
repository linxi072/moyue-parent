package com.moyue.message.channel;

/**
 * 触达渠道发送 SPI（P2-14 硬契约）。
 *
 * <p>新增渠道 = 新增一个 {@code @Component} 实现（实现 {@link #channel()} 与 {@link #send(ChannelMessage)}），
 * 业务代码零改动；分发器以 {@code List<ChannelSender>} 注入并按 {@link MessageChannel} 路由。</p>
 *
 * <p>约定：单一渠道失败不得影响其它渠道（分发器逐渠道 try/catch）；桩实现返回
 * {@link ChannelSendResult#pending(String)} 且不抛异常。</p>
 */
public interface ChannelSender {

    /** 渠道标识 */
    MessageChannel channel();

    /** 统一发送语义 */
    ChannelSendResult send(ChannelMessage msg);
}

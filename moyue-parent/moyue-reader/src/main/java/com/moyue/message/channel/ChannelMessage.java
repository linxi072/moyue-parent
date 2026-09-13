package com.moyue.message.channel;

import lombok.Data;

/**
 * 渠道发送载体（P2-14 渠道 SPI 入参）。
 *
 * <p>由 {@code MessageDispatcher} 组装后透传给各 {@link ChannelSender} 实现；
 * 各渠道按需取用 {@code target} / {@code title} / {@code content} 等字段。</p>
 */
@Data
public class ChannelMessage {

    /** 接收用户 ID */
    private Long userId;

    /** 目标渠道 */
    private MessageChannel channel;

    /** 模板编码 */
    private String templateCode;

    /** 投递地址（邮件渠道为邮箱、短信渠道为手机号；站内信/推送可空） */
    private String target;

    /** 渲染后的标题 */
    private String title;

    /** 渲染后的正文 */
    private String content;

    /** 业务类型，如 AUDIT / REPORT */
    private String bizType;

    /** 业务主键 */
    private Long bizId;
}

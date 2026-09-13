package com.moyue.message.channel;

/**
 * 触达渠道枚举（P2-14 渠道 SPI 硬契约）。
 *
 * <p>{@code code} 与 DB 约定一致：{@code message_channel_record.channel} /
 * {@code message_template.channels} 均使用 1/2/3/4。</p>
 */
public enum MessageChannel {

    /** 站内信（真实现，写 notice 表） */
    INBOX(1),

    /** 邮件（真实现，JavaMail） */
    EMAIL(2),

    /** 短信（桩实现） */
    SMS(3),

    /** 推送（桩实现） */
    PUSH(4);

    private final int code;

    MessageChannel(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 按渠道码解析枚举；未知码返回 {@code null}（调用方据此跳过非法渠道）。
     *
     * @param code 渠道码：1 站内信 / 2 邮件 / 3 短信 / 4 推送
     */
    public static MessageChannel fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MessageChannel channel : values()) {
            if (channel.code == code) {
                return channel;
            }
        }
        return null;
    }
}

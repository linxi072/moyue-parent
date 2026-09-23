package com.moyue.message.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单渠道发送结果（P2-14 渠道 SPI 硬契约）。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>{@code success=true}：渠道已真实投递成功（如站内信落库、邮件经 SMTP 发出）；</li>
 *   <li>{@code pending=true}：渠道未接入 / 异步待发（如短信、推送桩实现），不算失败，
 *       亦<b>不抛异常</b>；</li>
 *   <li>{@code detail}：明细说明（成功摘要、失败原因或「未接入供应商」等）。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSendResult {

    /** 是否成功投递 */
    private boolean success;

    /** 是否待发（桩实现 / 异步；非失败） */
    private boolean pending;

    /** 明细说明 */
    private String detail;

    /** 成功结果 */
    public static ChannelSendResult success(String detail) {
        return new ChannelSendResult(true, false, detail);
    }

    /** 待发结果（渠道未接入，不算失败） */
    public static ChannelSendResult pending(String detail) {
        return new ChannelSendResult(false, true, detail);
    }

    /** 失败结果 */
    public static ChannelSendResult failure(String detail) {
        return new ChannelSendResult(false, false, detail);
    }
}

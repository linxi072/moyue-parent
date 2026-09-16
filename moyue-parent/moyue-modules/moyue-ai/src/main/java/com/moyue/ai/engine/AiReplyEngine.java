package com.moyue.ai.engine;

/**
 * AI 回复引擎接口（可插拔）。
 *
 * <p>内置实现：</p>
 * <ul>
 *   <li>{@code LlmReplyEngine}（@Primary，真实大模型，配置驱动，缺密钥/未启用则降级）；</li>
 *   <li>{@code KeywordRuleReplyEngine}（关键词规则，兜底，永远可用）。</li>
 * </ul>
 *
 * <p>引擎返回 {@code ReplyResult.content == null} 表示无法回答，会话层（AiService）级联到下一引擎；
 * {@code confident=false} 表示把握不足，会话层追加「转人工」提示。</p>
 */
public interface AiReplyEngine {

    /** 根据上下文生成回复；content 为 null 表示本引擎无法回答（调用方应降级） */
    ReplyResult reply(ReplyContext context);

    /** 引擎标识，便于前端展示与日志排查 */
    String engineName();
}

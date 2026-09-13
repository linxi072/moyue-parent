package com.moyue.ai.engine;

/**
 * AI 回复引擎接口（可插拔）。
 * 当前内置实现为 KeywordRuleReplyEngine（关键词规则匹配 + 转人工兜底）；
 * 后续接入真实大模型时，新增实现类并标注 @Primary（或改用配置选择）即可，
 * 会话 / 消息 / 接口链路无需改动。
 */
public interface AiReplyEngine {

    /** 根据用户输入生成回复（实现须自行兜底，永不返回 null / 空串） */
    String reply(String userContent);

    /** 引擎标识，便于前端展示与日志排查 */
    String engineName();
}

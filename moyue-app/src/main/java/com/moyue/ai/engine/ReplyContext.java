package com.moyue.ai.engine;

import lombok.Data;

import java.util.List;

/**
 * 回复引擎输入上下文：当前提问 + 会话上下文（历史轮次 + 可选 RAG 召回片段）。
 * <p>引擎按能力消费：KeywordRuleReplyEngine 仅用 content；LlmReplyEngine 用 history + ragContext
 * 拼接系统提示词。引擎不应抛出 NPE——调用方（AiService）保证非空字段合理。</p>
 */
@Data
public class ReplyContext {

    /** 用户 ID（可选，仅日志排查用） */
    private Long userId;

    /** 会话 ID（可选，仅日志排查用） */
    private Long sessionId;

    /** 当前用户提问（必填） */
    private String content;

    /** 历史轮次（不含当前提问），按时间升序；可为空 */
    private List<ChatTurn> history;

    /** RAG 召回片段拼接（可为空，仅 LLM 引擎注入参考知识库） */
    private String ragContext;
}

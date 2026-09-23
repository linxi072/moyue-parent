package com.moyue.ai.engine;

import com.moyue.ai.client.LlmGatewayClient;
import com.moyue.ai.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型回复引擎（P1-1 真实接入 + 优雅降级，@Primary 优先于关键字引擎）。
 *
 * <p>配置驱动：{@code moyue.ai.llm.enabled=true} 且 {@code apiKey} 非空时，经标准库 HttpClient
 * 调用 OpenAI 兼容网关，将「系统提示词 + RAG 召回知识库 + 历史多轮 + 当前提问」拼为 messages 真实生成；
 * 未启用 / 缺少密钥 / 网关异常 / 返回空时返回 {@code null content}，由 AiService 级联到
 * {@code KeywordRuleReplyEngine}（<b>绝不抛异常，逐引擎隔离</b>）。无密钥即降级，业务代码零改动。</p>
 */
@Slf4j
@Component
@Primary
public class LlmReplyEngine implements AiReplyEngine {

    private final LlmProperties props;
    private final LlmGatewayClient gatewayClient;

    @Autowired
    public LlmReplyEngine(LlmProperties props, LlmGatewayClient gatewayClient) {
        this.props = props;
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String engineName() {
        return "llm-" + (props.getModel() == null ? "unknown" : props.getModel());
    }

    @Override
    public ReplyResult reply(ReplyContext context) {
        if (!props.isEnabled() || isBlank(props.getApiKey())) {
            // 未启用或缺少密钥：返回 null content，由 AiService 级联到关键字引擎
            return null;
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        String system = props.getSystemPrompt();
        if (context.getRagContext() != null && !context.getRagContext().isBlank()) {
            system += "\n\n参考知识库：\n" + context.getRagContext();
        }
        messages.add(msg("system", system));

        if (context.getHistory() != null) {
            int take = props.getMaxHistory() > 0 ? props.getMaxHistory() : context.getHistory().size();
            List<ChatTurn> history = context.getHistory();
            int from = Math.max(0, history.size() - take);
            for (int i = from; i < history.size(); i++) {
                ChatTurn t = history.get(i);
                messages.add(msg(t.getRole() == 1 ? "user" : "assistant", t.getContent()));
            }
        }
        messages.add(msg("user", context.getContent() == null ? "" : context.getContent()));

        try {
            String content = gatewayClient.chat(messages);
            return new ReplyResult(content, true, engineName());
        } catch (Exception ex) {
            // 任何失败均降级到关键字引擎（不向上抛，不阻断对话主流程）
            log.warn("LLM 生成失败，降级到关键字引擎 userId={}, err={}", context.getUserId(), ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

package com.moyue.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型网关配置（前缀 {@code moyue.ai.llm}）。
 *
 * <p>默认 {@code enabled=false}：未配置供应商时 {@link com.moyue.ai.engine.LlmReplyEngine}
 * 返回 null content，由 AiService 级联到 {@code KeywordRuleReplyEngine} 兜底（优雅降级，不抛异常）。
 * 运维在 yml / Nacos 配置 {@code moyue.ai.llm.enabled=true} + {@code apiKey} + {@code endpoint}
 * 后即可真实调用大模型，业务代码零改动。</p>
 *
 * <p>协议为 OpenAI 兼容的 {@code /v1/chat/completions}（POST JSON + Bearer 鉴权），
 * 适配 OpenAI / 通义千问 / 文心等主流网关；密钥绝不打印日志。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.ai.llm")
public class LlmProperties {

    /** 是否启用真实大模型（默认 false：降级为关键字引擎） */
    private boolean enabled = false;

    /** 对话补全网关地址（OpenAI 兼容） */
    private String endpoint = "https://api.openai.com/v1/chat/completions";

    /** 鉴权密钥（Bearer 头；缺失即降级，绝不打印日志） */
    private String apiKey;

    /** 模型名 */
    private String model = "gpt-4o-mini";

    /** 网关超时（秒） */
    private int timeoutSeconds = 10;

    /** 注入历史轮次上限（0 表示不注入历史） */
    private int maxHistory = 10;

    /** 系统提示词 */
    private String systemPrompt = "你是墨阅小说网的智能客服小墨，回答要简洁、友好、准确，仅围绕平台业务"
            + "（签到积分、兑换、打赏、周边商城、书币会员、审核时效、账号安全等）。若用户问题超出知识范围，"
            + "请如实说明并建议转人工。";
}

package com.moyue.api.ai.client;

import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI 客服服务 Feign 客户端（moyue-ai）。
 * 返回类型包裹 R&lt;T&gt;，与 AiInternalController 内部端点结构一致。
 * 供 moyue-search 管理端「全量重建问答索引」时分页拉取全量问答数据（仅服务间调用）。
 *
 * <p>contextId：本服务未来可能新增多个 @FeignClient（name 同为 moyue-ai），
 * 预先以 contextId 区分注册，避免 FeignClientSpecification 同名 bean 冲突。</p>
 */
@FeignClient(name = "moyue-ai", contextId = "aiClient", fallbackFactory = AiClientFallbackFactory.class)
public interface AiClient {

    /**
     * 分页拉取全量问答对（内部端点，不经网关）：一轮对话一条文档（question + answer），
     * 按 ai_message.id 升序；供管理端全量重建 moyue-qa 索引。
     */
    @GetMapping("/api/v1/internal/qa/page")
    R<PageResult<QaIndexDTO>> pageQa(@RequestParam("page") int page, @RequestParam("size") int size);
}

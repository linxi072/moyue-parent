package com.moyue.api.search.client;

import com.moyue.api.search.dto.QaContextDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI 客服问答检索 Feign 客户端（moyue-search）。
 * 供 moyue-ai 在 LLM 生成前做 RAG 召回（仅内部服务间调用，不经网关）。
 */
@FeignClient(name = "moyue-search", contextId = "qaSearchClient", fallbackFactory = QaSearchClientFallbackFactory.class)
public interface QaSearchClient {

    /** 按问题检索 topK 问答片段，返回知识库参考文本 */
    @GetMapping("/api/v1/internal/search/qa/retrieve")
    R<QaContextDTO> retrieveContext(@RequestParam("question") String question);
}

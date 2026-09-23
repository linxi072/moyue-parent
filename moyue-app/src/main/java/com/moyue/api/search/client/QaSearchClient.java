package com.moyue.api.search.client;

import com.moyue.api.search.dto.QaContextDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.search.service.QaSearchService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 客服问答检索进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-search) 已移除 OpenFeign，改为直接注入 {@link QaSearchService} 委托调用，
 * 供 moyue-ai 在 LLM 生成前做 RAG 召回。
 */
@Component
public class QaSearchClient {

    private final QaSearchService qaSearchService;

    public QaSearchClient(QaSearchService qaSearchService) {
        this.qaSearchService = qaSearchService;
    }

    /** 按问题检索 topK 问答片段，返回知识库参考文本 */
    public R<QaContextDTO> retrieveContext(String question) {
        try {
            List<String> passages = qaSearchService.retrieveContext(question);
            QaContextDTO dto = new QaContextDTO();
            dto.setPassages(passages);
            return R.ok(dto);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

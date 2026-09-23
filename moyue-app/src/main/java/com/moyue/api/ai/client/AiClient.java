package com.moyue.api.ai.client;

import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.ai.service.AiService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.springframework.stereotype.Component;

/**
 * AI 客服服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-ai) 已移除 OpenFeign，改为直接注入 {@link AiService} 委托调用，
 * 供 moyue-search 管理端「全量重建问答索引」时分页拉取全量问答数据。
 */
@Component
public class AiClient {

    private final AiService aiService;

    public AiClient(AiService aiService) {
        this.aiService = aiService;
    }

    /** 分页拉取全量问答对（一轮对话一条文档） */
    public R<PageResult<QaIndexDTO>> pageQa(int page, int size) {
        try {
            return R.ok(aiService.pageQaForIndex(page, size));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

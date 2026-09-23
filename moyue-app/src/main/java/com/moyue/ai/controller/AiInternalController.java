package com.moyue.ai.controller;

import com.moyue.ai.service.AiService;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 客服内部端点（服务间调用，仅供 Feign 使用）。
 * 路径 /api/v1/internal/** 不在网关任何路由内，外部不可达。
 * 分页拉取问答索引载荷：供 moyue-search 管理端全量重建 moyue-qa 索引。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class AiInternalController {

    @Autowired
    private AiService aiService;

    /** 分页拉取全量问答对（一轮对话一条文档，按 ai_message.id 升序） */
    @GetMapping("/qa/page")
    public R<PageResult<QaIndexDTO>> pageQa(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "100") int size) {
        return R.ok(aiService.pageQaForIndex(page, size));
    }
}

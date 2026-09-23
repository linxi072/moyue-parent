package com.moyue.search.controller;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.search.document.QaDocument;
import com.moyue.search.service.QaSearchService;
import com.moyue.search.service.ReindexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 检索管理端接口：客服知识检索（AI 问答）+ 全量重建索引。
 * <p>完整前缀 /api/v1/admin/search，匹配网关 /api/v1/admin/search/** 路由，
 * 由 AdminRoleInterceptor（moyue-common）做 role=3 断言 + @RequiresPermissions 细粒度权限码。
 * QA 索引含用户对话数据，仅管理员可检索；全量重建为重操作，独立权限码管控。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/search")
public class AdminSearchController {

    @Autowired
    private QaSearchService qaSearchService;

    @Autowired
    private ReindexService reindexService;

    /**
     * 客服知识检索：question / answer 全文匹配，可选时间范围 filter。
     *
     * @param keyword   关键词（必填）
     * @param startTime 开始时间（可选，格式 yyyy-MM-dd HH:mm:ss，含边界）
     * @param endTime   结束时间（可选，格式 yyyy-MM-dd HH:mm:ss，含边界）
     * @param page      页码（默认 1）
     * @param size      每页大小（默认 20）
     */
    @GetMapping("/qa")
    @RequiresPermissions("system:search:qa:list")
    public R<PageResult<QaDocument>> searchQa(@RequestParam String keyword,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(qaSearchService.search(keyword, startTime, endTime, page, size));
    }

    /**
     * 全量重建索引：type=book（书籍）/ chapter（已发布章节）/ qa（AI 问答）。
     * 经 Feign 从内容域 / AI 域分页拉全量覆盖写入（幂等，可重复执行）；下游不可用时报错中止。
     */
    @PostMapping("/reindex/{type}")
    @RequiresPermissions("system:search:reindex")
    public R<Long> reindex(@PathVariable String type) {
        return R.ok(reindexService.reindex(type));
    }
}

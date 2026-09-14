package com.moyue.chapter.controller;

import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章节内部端点（服务间调用，仅供 Feign 使用）。
 * 路径 /api/v1/internal/** 不在网关任何路由内，外部不可达；
 * 也不命中 AdminRoleInterceptor 的 /api/v1/admin/** 断言（该断言面向后台管理员入口）。
 * 分页拉取已发布章节索引载荷：供 moyue-search 管理端全量重建 moyue-chapter 索引。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class ChapterInternalController {

    @Autowired
    private ChapterService chapterService;

    /** 审核回写：status 2=已发布 / 3=已驳回 */
    @PutMapping("/chapters/{chapterId}/audit")
    public R<Void> audit(@PathVariable Long chapterId, @RequestParam Integer status) {
        chapterService.auditChapter(chapterId, status);
        return R.ok();
    }

    /** 分页拉取已发布章节索引载荷（按 chapter.id 升序，含截断后的正文） */
    @GetMapping("/chapter/page")
    public R<PageResult<ChapterIndexDTO>> pageChapters(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "100") int size) {
        return R.ok(chapterService.pageForIndex(page, size));
    }
}

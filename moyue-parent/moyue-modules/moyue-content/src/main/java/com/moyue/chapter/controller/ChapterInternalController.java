package com.moyue.chapter.controller;

import com.moyue.chapter.service.ChapterService;
import com.moyue.common.core.domain.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章节内部端点（服务间调用，仅供 Feign 使用）。
 * 路径 /api/v1/internal/** 不在网关任何路由内，外部不可达；
 * 也不命中 AdminRoleInterceptor 的 /api/v1/admin/** 断言（该断言面向后台管理员入口）。
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
}

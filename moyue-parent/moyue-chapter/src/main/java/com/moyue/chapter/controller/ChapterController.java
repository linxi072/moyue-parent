package com.moyue.chapter.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章节接口：章节详情 / 目录分页。
 * 路径前缀 /api/v1 与网关路由、Feign ChapterClient 保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    /** 章节正文（供 Feign ChapterClient 调用，路径必须为 /api/v1/chapters/{chapterId}） */
    @GetMapping("/chapters/{chapterId}")
    public R<ChapterEntity> getChapter(@PathVariable Long chapterId) {
        ChapterEntity chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return R.ok(chapter);
    }

    /** 作品目录分页（按 bookId 查询） */
    @GetMapping("/chapters")
    public R<PageResult<ChapterEntity>> listChapters(@RequestParam Long bookId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(chapterService.listByBook(bookId, page, size));
    }
}

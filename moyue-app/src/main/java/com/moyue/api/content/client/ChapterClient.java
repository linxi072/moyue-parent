package com.moyue.api.content.client;

import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.springframework.stereotype.Component;

/**
 * 章节服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-content) 已移除 OpenFeign，改为直接注入 {@link ChapterService} 委托调用。
 */
@Component
public class ChapterClient {

    private final ChapterService chapterService;

    public ChapterClient(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    /** 章节正文 */
    public R<ChapterDTO> getChapter(Long chapterId) {
        try {
            return R.ok(ChapterService.toDto(chapterService.getById(chapterId)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 审核回写章节状态（status 2=已发布 / 3=已驳回） */
    public R<Void> auditChapter(Long chapterId, Integer status) {
        try {
            chapterService.auditChapter(chapterId, status);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 分页拉取全量已发布章节（内部索引重建用） */
    public R<PageResult<ChapterIndexDTO>> pageChapters(int page, int size) {
        try {
            return R.ok(chapterService.pageForIndex(page, size));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 激活到点的定时章节，返回本次实际激活的章节条数 */
    public R<Integer> activateScheduledChapters() {
        try {
            return R.ok(chapterService.activateScheduledChapters());
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

package com.moyue.api.search.client;

import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.search.document.BookDocument;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.document.QaDocument;
import com.moyue.search.repository.ChapterSearchRepository;
import com.moyue.search.service.QaSearchService;
import com.moyue.search.service.SearchService;
import org.springframework.stereotype.Component;

/**
 * 检索索引同步进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-search) 已移除 OpenFeign；monolith 中索引写入由本进程内的
 * {@link SearchService}（书籍）、{@link ChapterSearchRepository}（章节）、{@link QaSearchService}（问答）
 * 直接完成（原 SearchInternalController 的三个端点落到同一进程），故委托到这些真实协作 Bean，
 * 等价于原先「经 Feign 推到 moyue-search 覆盖写索引」的语义，调用方无需感知。
 */
@Component
public class SearchIndexClient {

    private final SearchService searchService;
    private final ChapterSearchRepository chapterSearchRepository;
    private final QaSearchService qaSearchService;

    public SearchIndexClient(SearchService searchService,
                             ChapterSearchRepository chapterSearchRepository,
                             QaSearchService qaSearchService) {
        this.searchService = searchService;
        this.chapterSearchRepository = chapterSearchRepository;
        this.qaSearchService = qaSearchService;
    }

    /** 索引一本书（幂等覆盖更新） */
    public R<BookIndexDTO> indexBook(BookIndexDTO book) {
        try {
            searchService.index(BookDocument.fromIndexDto(book));
            return R.ok(book);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 下架同步：物理删除索引文档 */
    public R<Void> removeBook(Long bookId) {
        try {
            searchService.remove(bookId);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 索引一章（已发布章节入库，幂等覆盖更新） */
    public R<ChapterIndexDTO> indexChapter(ChapterIndexDTO chapter) {
        try {
            chapterSearchRepository.save(ChapterDocument.fromIndexDto(chapter));
            return R.ok(chapter);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 章节下架 / 删除同步：物理删除章节索引文档 */
    public R<Void> removeChapter(Long chapterId) {
        try {
            chapterSearchRepository.deleteById(chapterId);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 索引一轮 AI 客服问答（幂等覆盖更新） */
    public R<QaIndexDTO> indexQa(QaIndexDTO qa) {
        try {
            qaSearchService.index(QaDocument.fromIndexDto(qa));
            return R.ok(qa);
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 删除单条问答索引文档 */
    public R<Void> removeQa(Long messageId) {
        try {
            qaSearchService.remove(messageId);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 按会话删除该会话全部问答索引文档 */
    public R<Void> removeQaBySession(Long sessionId) {
        try {
            qaSearchService.removeBySession(sessionId);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

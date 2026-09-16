package com.moyue.search.controller;

import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.api.search.dto.QaContextDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.R;
import com.moyue.search.document.BookDocument;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.document.QaDocument;
import com.moyue.search.service.ChapterSearchService;
import com.moyue.search.service.QaSearchService;
import com.moyue.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索内部端点：书籍 / 章节 / AI 客服问答索引同步，仅供服务间调用。
 * {@code /api/v1/internal/**} 不经网关（服务间经 Nacos {@code lb://} 直连）。
 *
 * <p>书籍索引由 {@code moyue-content} 经 {@code SearchIndexClient} 调用（P2-13，路径零变更）；
 * 章节索引由 {@code moyue-content} 在章节发布 / 删除时推送；问答索引由 {@code moyue-ai}
 * 在每轮对话落库成功后推送。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class SearchInternalController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private ChapterSearchService chapterSearchService;

    @Autowired
    private QaSearchService qaSearchService;

    /**
     * 索引一本书：收 {@link BookIndexDTO} → 构建 {@link BookDocument} 落索引并回写 hotScore / clickCount。
     * bookId 为文档 _id，重复推送覆盖更新（幂等）。
     */
    @PostMapping("/internal/search/books/_index")
    public R<BookDocument> indexBook(@RequestBody BookIndexDTO book) {
        BookDocument doc = BookDocument.fromIndexDto(book);
        return R.ok(searchService.index(doc));
    }

    /** 下架同步：物理删除索引文档 */
    @DeleteMapping("/internal/search/books/{bookId}")
    public R<Void> removeBook(@PathVariable Long bookId) {
        searchService.remove(bookId);
        return R.ok();
    }

    /**
     * 索引一章：仅已发布章节入库（内容域侧保证），chapterId 为 _id，重复推送覆盖更新（幂等）。
     */
    @PostMapping("/internal/search/chapters/_index")
    public R<ChapterIndexDTO> indexChapter(@RequestBody ChapterIndexDTO chapter) {
        ChapterDocument doc = ChapterDocument.fromIndexDto(chapter);
        if (doc != null) {
            chapterSearchService.index(doc);
        }
        return R.ok(chapter);
    }

    /** 章节下架 / 删除同步：物理删除章节索引文档 */
    @DeleteMapping("/internal/search/chapters/{chapterId}")
    public R<Void> removeChapter(@PathVariable Long chapterId) {
        chapterSearchService.remove(chapterId);
        return R.ok();
    }

    /** 索引一轮 AI 客服问答：messageId 为 _id，重复推送覆盖更新（幂等） */
    @PostMapping("/internal/search/qa/_index")
    public R<QaIndexDTO> indexQa(@RequestBody QaIndexDTO qa) {
        QaDocument doc = QaDocument.fromIndexDto(qa);
        if (doc != null) {
            qaSearchService.index(doc);
        }
        return R.ok(qa);
    }

    /** 删除单条问答索引文档 */
    @DeleteMapping("/internal/search/qa/{messageId}")
    public R<Void> removeQa(@PathVariable Long messageId) {
        qaSearchService.remove(messageId);
        return R.ok();
    }

    /** 按会话删除该会话全部问答索引文档 */
    @DeleteMapping("/internal/search/qa/session/{sessionId}")
    public R<Void> removeQaBySession(@PathVariable Long sessionId) {
        qaSearchService.removeBySession(sessionId);
        return R.ok();
    }

    /** AI 客服 RAG 召回：按问题检索 topK 问答片段，供大模型注入参考知识库 */
    @GetMapping("/internal/search/qa/retrieve")
    public R<QaContextDTO> retrieveContext(@RequestParam String question) {
        QaContextDTO dto = new QaContextDTO();
        dto.setPassages(qaSearchService.retrieveContext(question));
        return R.ok(dto);
    }
}

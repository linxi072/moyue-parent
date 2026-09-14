package com.moyue.api.search.client;

import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 检索服务 Feign 客户端（moyue-search）。
 * 返回类型包裹 R&lt;T&gt;，与 SearchInternalController 内部端点结构一致。
 * 供 moyue-content 在书籍创建 / 更新 / 下架时同步 ES 索引（P2-13）。
 */
@FeignClient(name = "moyue-search", fallbackFactory = SearchIndexClientFallbackFactory.class)
public interface SearchIndexClient {

    /** 索引一本书（bookId 为 _id，重复推送覆盖更新，幂等） */
    @PostMapping("/api/v1/internal/search/books/_index")
    R<BookIndexDTO> indexBook(@RequestBody BookIndexDTO book);

    /** 下架同步：物理删除索引文档 */
    @DeleteMapping("/api/v1/internal/search/books/{bookId}")
    R<Void> removeBook(@PathVariable("bookId") Long bookId);

    /** 索引一章：仅已发布章节入库（chapterId 为 _id，重复推送覆盖更新，幂等） */
    @PostMapping("/api/v1/internal/search/chapters/_index")
    R<ChapterIndexDTO> indexChapter(@RequestBody ChapterIndexDTO chapter);

    /** 章节下架 / 删除同步：物理删除章节索引文档 */
    @DeleteMapping("/api/v1/internal/search/chapters/{chapterId}")
    R<Void> removeChapter(@PathVariable("chapterId") Long chapterId);

    /** 索引一轮 AI 客服问答（messageId 为 _id，重复推送覆盖更新，幂等） */
    @PostMapping("/api/v1/internal/search/qa/_index")
    R<QaIndexDTO> indexQa(@RequestBody QaIndexDTO qa);

    /** 删除单条问答索引文档 */
    @DeleteMapping("/api/v1/internal/search/qa/{messageId}")
    R<Void> removeQa(@PathVariable("messageId") Long messageId);

    /** 按会话删除该会话全部问答索引文档 */
    @DeleteMapping("/api/v1/internal/search/qa/session/{sessionId}")
    R<Void> removeQaBySession(@PathVariable("sessionId") Long sessionId);
}

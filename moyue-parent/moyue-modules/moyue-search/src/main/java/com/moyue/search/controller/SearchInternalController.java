package com.moyue.search.controller;

import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.common.core.domain.R;
import com.moyue.search.document.BookDocument;
import com.moyue.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检索内部端点：书籍索引同步（P2-13 S-3），仅供服务间调用。
 * {@code /api/v1/internal/**} 不经网关（服务间经 Nacos {@code lb://} 直连）。
 *
 * <p>由 {@code moyue-content} 经 {@code SearchIndexClient} 调用；路径与迁出前零变更。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class SearchInternalController {

    @Autowired
    private SearchService searchService;

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
}

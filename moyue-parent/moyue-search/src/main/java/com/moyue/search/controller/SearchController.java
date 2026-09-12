package com.moyue.search.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import com.moyue.search.document.BookDocument;
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
 * 搜索接口：书籍全文检索 + 内部索引同步。
 * 路径前缀 /api/v1 与网关路由保持一致；
 * /internal/** 不在网关路由白名单内，仅供服务间调用（book 服务推送文档）。
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    @Autowired
    private SearchService searchService;

    /** 书籍全文检索：title / authorName / categoryName / description 任一命中 */
    @GetMapping("/search/books")
    public R<PageResult<BookDocument>> searchBooks(@RequestParam String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(searchService.search(keyword, page, size));
    }

    /** 服务间内部端点：索引一本书（bookId 为 _id，重复推送覆盖更新） */
    @PostMapping("/internal/search/books/_index")
    public R<BookDocument> indexBook(@RequestBody BookDocument doc) {
        return R.ok(searchService.index(doc));
    }

    /** 服务间内部端点：下架同步，物理删除索引文档 */
    @DeleteMapping("/internal/search/books/{bookId}")
    public R<Void> removeBook(@PathVariable Long bookId) {
        searchService.remove(bookId);
        return R.ok();
    }
}

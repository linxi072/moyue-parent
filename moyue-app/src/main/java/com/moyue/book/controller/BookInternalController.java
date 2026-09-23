package com.moyue.book.controller;

import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.book.service.BookService;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书籍内部端点（服务间调用，仅供 Feign 使用）。
 * 路径 /api/v1/internal/** 不在网关任何路由内，外部不可达。
 * 分页拉取书籍索引载荷：供 moyue-search 管理端全量重建 moyue_book 索引。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class BookInternalController {

    @Autowired
    private BookService bookService;

    /** 分页拉取书籍索引载荷（按 book.id 升序，含热度分 / 作者昵称） */
    @GetMapping("/book/page")
    public R<PageResult<BookIndexDTO>> pageBooks(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "100") int size) {
        return R.ok(bookService.pageForIndex(page, size));
    }
}

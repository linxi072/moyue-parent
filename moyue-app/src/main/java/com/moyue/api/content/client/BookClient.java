package com.moyue.api.content.client;

import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.book.service.BookService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.springframework.stereotype.Component;

/**
 * 书城服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-content) 已移除 OpenFeign，改为直接注入 {@link BookService} 委托调用。
 */
@Component
public class BookClient {

    private final BookService bookService;

    public BookClient(BookService bookService) {
        this.bookService = bookService;
    }

    /** 书籍详情 */
    public R<BookSummaryDTO> getBook(Long bookId) {
        try {
            return R.ok(bookService.detail(bookId));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 分页查询书籍（不带分类过滤） */
    public R<PageResult<BookSummaryDTO>> listBooks(int page, int size) {
        try {
            return R.ok(bookService.listBooks(page, size, null));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 分页拉取全量书籍索引载荷（内部索引重建用） */
    public R<PageResult<BookIndexDTO>> pageBooks(int page, int size) {
        try {
            return R.ok(bookService.pageForIndex(page, size));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

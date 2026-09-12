package com.moyue.book.controller;

import com.moyue.api.dto.BookSummaryDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.book.service.BookService;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书城接口：书籍分页 / 详情。
 * 返回共享 DTO BookSummaryDTO，与 Feign BookClient 及前端契约保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class BookController {

    @Autowired
    private BookService bookService;

    /** 分页查询书籍（读取网关注入的 X-User-Id 上下文头） */
    @GetMapping("/books")
    public R<PageResult<BookSummaryDTO>> listBooks(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   HttpServletRequest request) {
        String userId = request.getHeader(Constants.USER_ID_HEADER);
        // userId 非空代表请求已通过网关鉴权
        return R.ok(bookService.listBooks(page, size));
    }

    /** 书籍详情 */
    @GetMapping("/books/{bookId}")
    public R<BookSummaryDTO> detail(@PathVariable Long bookId) {
        BookSummaryDTO dto = bookService.detail(bookId);
        if (dto == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return R.ok(dto);
    }
}

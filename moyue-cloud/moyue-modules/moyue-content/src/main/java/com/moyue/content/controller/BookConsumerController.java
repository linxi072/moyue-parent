package com.moyue.content.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.content.domain.dto.query.BookQuery;
import com.moyue.content.domain.vo.BookVO;
import com.moyue.content.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作品消费（C 端）：已发布作品列表 / 详情。
 *
 * <p>与后台 {@code BookController}（/api/v1/admin/content/books）分离：本控制器只读已发布作品，
 * 不暴露草稿与下架，且无需后台权限。读者可匿名浏览。
 *
 * @author moyue
 */
@Tag(name = "作品消费", description = "已发布作品列表与详情")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/books")
@RequiredArgsConstructor
public class BookConsumerController {

    private final BookService bookService;

    @Operation(summary = "已发布作品分页")
    @GetMapping
    public R<?> page(BookQuery query) {
        return R.ok(bookService.consumerPageBooks(query));
    }

    @Operation(summary = "作品详情")
    @GetMapping("/{bookId}")
    public R<BookVO> detail(@PathVariable Long bookId) {
        return R.ok(bookService.getBook(bookId));
    }
}

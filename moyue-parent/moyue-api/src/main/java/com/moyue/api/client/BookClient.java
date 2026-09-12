package com.moyue.api.client;

import com.moyue.api.dto.BookSummaryDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 书城服务 Feign 客户端（moyue-content）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<BookSummaryDTO>} 结构一致。
 */
@FeignClient(name = "moyue-content")
public interface BookClient {

    /** 书籍详情 */
    @GetMapping("/api/v1/books/{bookId}")
    R<BookSummaryDTO> getBook(@PathVariable("bookId") Long bookId);

    /** 分页查询书籍 */
    @GetMapping("/api/v1/books")
    R<PageResult<BookSummaryDTO>> listBooks(@RequestParam("page") int page, @RequestParam("size") int size);
}

package com.moyue.api.content.client;

import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 书城服务 Feign 客户端（moyue-content）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<BookSummaryDTO>} 结构一致。
 */
/**
 * 书籍服务 Feign 客户端（moyue-content）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<BookSummaryDTO>} 结构一致。
 *
 * <p>contextId：与同服务的 ChapterClient（name 同为 moyue-content）区分注册，
 * 避免 FeignClientSpecification 同名 bean 冲突（正解，替代 allow-bean-definition-overriding）。</p>
 */
@FeignClient(name = "moyue-content", contextId = "bookClient", fallbackFactory = BookClientFallbackFactory.class)
public interface BookClient {

    /** 书籍详情 */
    @GetMapping("/api/v1/books/{bookId}")
    R<BookSummaryDTO> getBook(@PathVariable("bookId") Long bookId);

    /** 分页查询书籍 */
    @GetMapping("/api/v1/books")
    R<PageResult<BookSummaryDTO>> listBooks(@RequestParam("page") int page, @RequestParam("size") int size);

    /**
     * 分页拉取全量书籍索引载荷（内部端点，不经网关，仅服务间调用）：按 book.id 升序；
     * 供 moyue-search 管理端全量重建 moyue_book 索引。
     */
    @GetMapping("/api/v1/internal/book/page")
    R<PageResult<BookIndexDTO>> pageBooks(@RequestParam("page") int page, @RequestParam("size") int size);
}

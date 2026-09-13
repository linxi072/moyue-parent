package com.moyue.search.controller;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import com.moyue.search.document.BookDocument;
import com.moyue.search.service.RecommendService;
import com.moyue.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索接口：书籍全文检索 + 推荐位（P2-13 S-1 / S-2）。
 * 路径前缀 /api/v1 与网关路由保持一致。
 *
 * <p>P2-13 由 moyue-content 整包迁入 moyue-search，包名与既有 HTTP 路径零变更：
 * {@code GET /api/v1/search/books} 仅新增可选参数（categoryId / sort），契约向后兼容。
 * 内部索引同步端点已迁至 {@link SearchInternalController}。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private RecommendService recommendService;

    /**
     * 书籍全文检索：title / authorName / categoryName / description 任一命中。
     *
     * @param keyword    关键词（必填）
     * @param categoryId 分类 ID（可选）
     * @param sort       排序：relevance（默认）/ hot / latest
     * @param page       页码（默认 1）
     * @param size       每页大小（默认 20）
     */
    @GetMapping("/search/books")
    public R<PageResult<BookDocument>> searchBooks(@RequestParam String keyword,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false, defaultValue = "relevance") String sort,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return R.ok(searchService.search(keyword, categoryId, sort, page, size));
    }

    /**
     * 推荐位：按热度取 TopN。
     *
     * @param limit 条数（默认 10，上限 {@code moyue.search.recommend.max-limit}）
     * @param sort  排序：hot（默认）/ latest
     */
    @GetMapping("/search/recommend")
    public R<List<BookDocument>> recommend(@RequestParam(defaultValue = "10") int limit,
                                           @RequestParam(defaultValue = "hot") String sort) {
        return R.ok(recommendService.recommend(limit, sort));
    }
}

package com.moyue.search.controller;

import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import com.moyue.search.document.BookDocument;
import com.moyue.search.dto.ChapterSearchResultDTO;
import com.moyue.search.service.ChapterSearchService;
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
    private ChapterSearchService chapterSearchService;

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
     * 章节全文检索：章节标题（^2）/ 正文 multi_match 命中，返回正文高亮片段。
     *
     * @param keyword 关键词（必填）
     * @param bookId  作品 ID（可选，限定在单部作品内搜章节）
     * @param page    页码（默认 1）
     * @param size    每页大小（默认 20）
     */
    @GetMapping("/search/chapters")
    public R<PageResult<ChapterSearchResultDTO>> searchChapters(@RequestParam String keyword,
                                                                @RequestParam(required = false) Long bookId,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return R.ok(chapterSearchService.search(keyword, bookId, page, size));
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

    /**
     * 个性化推荐位（P1-3）：按用户兴趣画像（类目 / 作者偏好）重排候选、排除已在书架书籍；
     * userId 缺省或画像为空时回退热门推荐。实际 userId 由网关注入 X-User-Id，此处参数便于内部/测试调用。
     *
     * @param userId 用户 ID（可选）
     * @param limit  条数（默认 10，上限 {@code moyue.search.recommend.max-limit}）
     */
    @GetMapping("/recommend/personal")
    public R<List<BookDocument>> personalize(@RequestParam(required = false) Long userId,
                                             @RequestParam(defaultValue = "10") int limit) {
        return R.ok(recommendService.personalizeRecommend(userId, limit));
    }
}

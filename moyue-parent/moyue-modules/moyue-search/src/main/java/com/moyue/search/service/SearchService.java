package com.moyue.search.service;

import com.moyue.common.core.domain.PageResult;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.constant.SearchSort;
import com.moyue.search.document.BookDocument;
import com.moyue.search.repository.BookSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍检索业务：
 *  - index：文档写入（幂等，bookId 为 _id 覆盖更新）
 *  - remove：下架同步（物理删文档）
 *  - search：title / authorName / categoryName / description 任一命中即返回，
 *    仅返回 status ∈ {1,2}（连载中 / 已完结）；可选 categoryId term 过滤；
 *    支持 sort：{@code relevance}（默认，_score 降序）/ {@code hot}（hotScore 降序）/
 *    {@code latest}（updateTime 降序）。
 * ES 不可用时连接异常自然抛出，由全局异常处理器统一转 40001。
 *
 * <p>P2-13 由 moyue-content 整包迁入 moyue-search，包名零变更；T04 增强
 * categoryId 过滤与 sort 排序。</p>
 */
@Service
public class SearchService {

    /** 参与检索的字段状态白名单：1 连载中 / 2 已完结 */
    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_FINISHED = 2;

    /** 排序字段：热度 */
    private static final String FIELD_HOT_SCORE = "hotScore";
    /** 排序字段：更新时间 */
    private static final String FIELD_UPDATE_TIME = "updateTime";

    @Autowired
    private BookSearchRepository bookSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SearchProperties searchProperties;

    /** 索引一本书（重复推送覆盖更新，幂等） */
    public BookDocument index(BookDocument doc) {
        return bookSearchRepository.save(doc);
    }

    /** 下架同步：物理删除索引文档 */
    public void remove(Long bookId) {
        bookSearchRepository.deleteById(bookId);
    }

    /**
     * 关键词检索：四字段任一 contains 命中；可选 categoryId 过滤；分页 page 从 1 起；
     * 排序按 {@code sort} 装配（非法值回退 relevance）。
     *
     * @param keyword    关键词（必填，四字段 OR 匹配）
     * @param categoryId 分类 ID，非空时追加 term 过滤
     * @param sort       排序方式：relevance / hot / latest
     * @param page       页码，从 1 起（小于 1 时按 1 处理）
     * @param size       每页大小（小于等于 0 时取配置默认值）
     * @return 分页检索结果
     */
    public PageResult<BookDocument> search(String keyword, Long categoryId, String sort, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size > 0 ? size : searchProperties.getDefaultPageSize();

        Criteria criteria = new Criteria("title").contains(keyword)
                .or(new Criteria("authorName").contains(keyword))
                .or(new Criteria("categoryName").contains(keyword))
                .or(new Criteria("description").contains(keyword))
                // 仅连载中(1) / 已完结(2)参与检索；下架同步延迟时在查询层兜底
                .and(new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED));
        if (categoryId != null) {
            criteria = criteria.and(new Criteria("categoryId").is(categoryId));
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(safePage - 1, safeSize));
        applySort(query, SearchSort.fromValue(sort));

        SearchHits<BookDocument> hits = elasticsearchOperations.search(query, BookDocument.class);
        return toPageResult(hits, safePage, safeSize);
    }

    /** 按排序方式装配 ES 排序；relevance 不显式排序，走 ES 默认 _score 降序 */
    private void applySort(CriteriaQuery query, SearchSort sort) {
        switch (sort) {
            case HOT:
                query.addSort(Sort.by(Sort.Direction.DESC, FIELD_HOT_SCORE));
                break;
            case LATEST:
                query.addSort(Sort.by(Sort.Direction.DESC, FIELD_UPDATE_TIME));
                break;
            case RELEVANCE:
            default:
                // ES 默认相关度排序（_score desc）
                break;
        }
    }

    /** SearchHits → PageResult */
    private PageResult<BookDocument> toPageResult(SearchHits<BookDocument> hits, int page, int size) {
        PageResult<BookDocument> result = new PageResult<>();
        result.setTotal(hits.getTotalHits());
        result.setPage(page);
        result.setSize(size);
        List<BookDocument> records = new ArrayList<>();
        for (SearchHit<BookDocument> hit : hits.getSearchHits()) {
            records.add(hit.getContent());
        }
        result.setRecords(records);
        return result;
    }
}

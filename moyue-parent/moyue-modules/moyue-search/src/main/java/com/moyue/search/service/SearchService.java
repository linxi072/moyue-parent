package com.moyue.search.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.moyue.common.core.domain.PageResult;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.constant.SearchSort;
import com.moyue.search.document.BookDocument;
import com.moyue.search.repository.BookSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 书籍检索业务：
 *  - index：文档写入（幂等，bookId 为 _id 覆盖更新）
 *  - remove：下架同步（物理删文档）
 *  - search：title（^3）/ authorName（^2）/ categoryName / description 全文匹配
 *    （minimum_should_match 75%），仅返回 status ∈ {1,2}（连载中 / 已完结）；
 *    可选 categoryId term 过滤；支持 sort：{@code relevance}（默认，_score 降序）/
 *    {@code hot}（hotScore 降序）/ {@code latest}（updateTime 降序）。
 *
 * <p>提效改造（T41）：CriteriaQuery → NativeQuery（Lambda DSL）。status 白名单与 categoryId
 * 过滤从原来的 query context（参与打分）移入 bool <b>filter context</b>——filter 不算分且其
 * 结果可被 ES 节点级 filter cache 缓存复用；全文匹配合并为单条 multi_match，减少一次查询重构。
 * 对外行为零变更：默认 relevance、hot / latest 排序、status 白名单。</p>
 *
 * <p>ES 不可用时连接异常自然抛出，由全局异常处理器统一转 40001。</p>
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
     * 关键词检索：四字段 multi_match（minimum_should_match 75%）任一命中即返回；
     * status 白名单与可选 categoryId 过滤走 filter context；分页 page 从 1 起；
     * 排序按 {@code sort} 装配（非法值回退 relevance）。
     *
     * @param keyword    关键词（必填，四字段匹配）
     * @param categoryId 分类 ID，非空时追加 term 过滤
     * @param sort       排序方式：relevance / hot / latest
     * @param page       页码，从 1 起（小于 1 时按 1 处理）
     * @param size       每页大小（小于等于 0 时取配置默认值）
     * @return 分页检索结果
     */
    public PageResult<BookDocument> search(String keyword, Long categoryId, String sort, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size > 0 ? size : searchProperties.getDefaultPageSize();

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                // 全文匹配（query context 计分）+ 过滤条件（filter context：不计分、可缓存）
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.multiMatch(mm -> mm
                            .query(keyword)
                            .fields("title^3", "authorName^2", "categoryName", "description")
                            .minimumShouldMatch("75%")));
                    // 仅连载中(1) / 已完结(2)参与检索，OR 语义：filter 内嵌 should bool（下架同步延迟时查询层兜底）
                    b.filter(f -> f.bool(fb -> {
                        fb.should(sh -> sh.term(t -> t.field("status").value(STATUS_ONGOING)));
                        fb.should(sh -> sh.term(t -> t.field("status").value(STATUS_FINISHED)));
                        fb.minimumShouldMatch("1");
                        return fb;
                    }));
                    if (categoryId != null) {
                        b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of(safePage - 1, safeSize));
        applySort(queryBuilder, SearchSort.fromValue(sort));

        SearchHits<BookDocument> hits = elasticsearchOperations.search(queryBuilder.build(), BookDocument.class);
        return toPageResult(hits, safePage, safeSize);
    }

    /** 按排序方式装配 ES 排序；relevance 不显式排序，走 ES 默认 _score 降序 */
    private void applySort(NativeQueryBuilder queryBuilder, SearchSort sort) {
        switch (sort) {
            case HOT:
                queryBuilder.withSort(s -> s.field(f -> f.field(FIELD_HOT_SCORE).order(SortOrder.Desc)));
                break;
            case LATEST:
                queryBuilder.withSort(s -> s.field(f -> f.field(FIELD_UPDATE_TIME).order(SortOrder.Desc)));
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

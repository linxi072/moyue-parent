package com.moyue.search.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.moyue.common.core.domain.PageResult;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.constant.SearchSort;
import com.moyue.search.document.BookDocument;
import com.moyue.search.dto.BookSearchResult;
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
import java.util.Set;

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
 * <p>P1-5 检索质量调优：检索词经 {@link SynonymExpander} 双向扩展为等价词集合，构建
 * bool.should 多词召回（同义查询也命中）；主检索无命中时 {@link #searchWithCorrection} 经
 * {@link SpellCorrector} 取最近词二次召回并回显 {@code correctedKeyword}，提升无结果率。</p>
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

    @Autowired
    private SynonymExpander synonymExpander;

    @Autowired
    private SpellCorrector spellCorrector;

    /** 索引一本书（重复推送覆盖更新，幂等） */
    public BookDocument index(BookDocument doc) {
        return bookSearchRepository.save(doc);
    }

    /** 下架同步：物理删除索引文档 */
    public void remove(Long bookId) {
        bookSearchRepository.deleteById(bookId);
    }

    /**
     * 关键词检索：同义词扩展为多词 bool.should 召回；status 白名单与可选 categoryId 过滤走
     * filter context；分页 page 从 1 起；排序按 {@code sort} 装配（非法值回退 relevance）。
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
        Set<String> terms = synonymExpander.expand(keyword);

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(buildBookQuery(terms, categoryId))
                .withPageable(PageRequest.of(safePage - 1, safeSize));
        applySort(queryBuilder, SearchSort.fromValue(sort));

        SearchHits<BookDocument> hits = elasticsearchOperations.search(queryBuilder.build(), BookDocument.class);
        return toPageResult(hits, safePage, safeSize);
    }

    /**
     * 构建书籍检索 bool 查询（同义词扩展 + status 白名单 + 可选 categoryId 过滤）。
     * 包可见，供单测断言查询结构（同义词是否进入召回）。
     *
     * @param terms      扩展后的检索词集合（含原词）
     * @param categoryId 分类 ID（null 表示不限）
     * @return ES bool 查询
     */
    co.elastic.clients.elasticsearch._types.query_dsl.Query buildBookQuery(Set<String> terms, Long categoryId) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(b -> {
            // 全文匹配（query context 计分）：每个同义词一条 multi_match，should 任一命中
            b.must(m -> m.bool(mb -> {
                for (String term : terms) {
                    mb.should(sh -> sh.multiMatch(mm -> mm
                            .query(term)
                            .fields("title^3", "authorName^2", "categoryName", "description")
                            .minimumShouldMatch("75%")));
                }
                mb.minimumShouldMatch("1");
                return mb;
            }));
            // 仅连载中(1) / 已完结(2)参与检索，OR 语义：filter 内嵌 should bool
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
        }));
    }

    /**
     * 带纠错的检索：主检索无命中时按 {@link SpellCorrector} 取最近词二次召回，
     * 并在结果中回显 {@code correctedKeyword}（null 表示未纠错）。
     */
    public BookSearchResult searchWithCorrection(String keyword, Long categoryId, String sort, int page, int size) {
        PageResult<BookDocument> primary = search(keyword, categoryId, sort, page, size);
        BookSearchResult result = toResult(primary);
        if (primary.getTotal() == 0) {
            String corrected = spellCorrector.correct(keyword);
            if (corrected != null) {
                PageResult<BookDocument> correctedHits = search(corrected, categoryId, sort, page, size);
                result = toResult(correctedHits);
                result.setCorrectedKeyword(corrected);
            }
        }
        return result;
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

    /** PageResult → BookSearchResult（correctedKeyword 默认 null） */
    private BookSearchResult toResult(PageResult<BookDocument> page) {
        BookSearchResult result = new BookSearchResult();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPage(page.getPage());
        result.setSize(page.getSize());
        return result;
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

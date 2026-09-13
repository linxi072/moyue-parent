package com.moyue.search.service;

import com.moyue.search.config.SearchProperties;
import com.moyue.search.constant.SearchSort;
import com.moyue.search.document.BookDocument;
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
 * 推荐位服务（P2-13 S-2）：按热度取 TopN 书籍。
 *
 * <p>推荐位复用同一索引 {@code moyue_book}，仅取 status ∈ {1,2} 的文档，
 * 默认按 {@code hotScore} 降序；{@code sort=latest} 时按 {@code updateTime} 降序。
 * limit 上限由 {@code moyue.search.recommend.max-limit}（默认 50）配置。</p>
 */
@Service
public class RecommendService {

    /** 仅参与检索的字段状态白名单：1 连载中 / 2 已完结 */
    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_FINISHED = 2;

    private static final String FIELD_HOT_SCORE = "hotScore";
    private static final String FIELD_UPDATE_TIME = "updateTime";

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SearchProperties searchProperties;

    /**
     * 推荐位：按排序取 TopN（limit 上限受配置约束，最小 1）。
     *
     * @param limit 期望条数（默认 10，上限 {@code recommend.max-limit}）
     * @param sort  排序方式：hot（默认）/ latest / relevance（推荐位 relevance 视作 hot）
     * @return 推荐书籍列表
     */
    public List<BookDocument> recommend(int limit, String sort) {
        int maxLimit = Math.max(searchProperties.getRecommend().getMaxLimit(), 1);
        int safeLimit = Math.min(Math.max(limit, 1), maxLimit);

        Criteria criteria = new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED);
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(0, safeLimit));
        query.addSort(resolveSort(sort));

        SearchHits<BookDocument> hits = elasticsearchOperations.search(query, BookDocument.class);
        List<BookDocument> list = new ArrayList<>();
        for (SearchHit<BookDocument> hit : hits.getSearchHits()) {
            list.add(hit.getContent());
        }
        return list;
    }

    /** 推荐位排序：latest → updateTime 降序；其余（hot / relevance / 非法）→ hotScore 降序 */
    private Sort resolveSort(String sort) {
        SearchSort sortEnum = SearchSort.fromValue(sort);
        if (sortEnum == SearchSort.LATEST) {
            return Sort.by(Sort.Direction.DESC, FIELD_UPDATE_TIME);
        }
        return Sort.by(Sort.Direction.DESC, FIELD_HOT_SCORE);
    }
}

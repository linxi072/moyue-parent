package com.moyue.search.service;

import com.moyue.common.cache.CacheNames;
import com.moyue.search.document.BookDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
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
 * 榜单/排行榜服务（榜单模块）。
 *
 * <p>复用 {@code RecommendService} 的 ES 召回范式（status ∈ {1,2} → CriteriaQuery →
 * ElasticsearchOperations.search → 遍历 SearchHit 取 content），按不同维度排序产出四榜：
 * <ul>
 *   <li>hot（热门）：status ∈ {1,2}，按 hotScore 降序（含点击/收藏热度）；</li>
 *   <li>newest（新书）：status ∈ {1,2}，按 updateTime 降序；</li>
 *   <li>finished（完结）：status = 2，按 hotScore 降序；</li>
 *   <li>top-rated（评分）：status ∈ {1,2} 且 ratingCount ≥ {@link #MIN_REVIEWS}，按 ratingAvg 降序。</li>
 * </ul>
 * </p>
 *
 * <p>不修改 {@code RecommendService} 的召回逻辑，仅复用其结构；结果经 {@code @Cacheable}
 * （缓存名 {@link CacheNames#LEADERBOARD}，TTL 5 分钟，与推荐位同频）缓存，按 {@code type:limit} 维度分区。</p>
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    /** 仅参与检索的字段状态：1 连载中 / 2 已完结（与 RecommendService 保持一致） */
    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_FINISHED = 2;

    /** 排序字段名（与 RecommendService 及 BookDocument 索引字段一致） */
    private static final String FIELD_HOT_SCORE = "hotScore";
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_RATING_AVG = "ratingAvg";

    /** 评分榜最低评分数阈值，避免少量评价的书挤占榜单 */
    private static final long MIN_REVIEWS = 3L;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * 榜单查询：按类型取 TopN。
     *
     * @param type  榜单类型：hot（默认）/ newest / finished / top-rated
     * @param limit 期望条数（自动收敛到 [1,100]）
     * @return 榜单书籍列表（按对应维度降序）
     */
    @Cacheable(cacheNames = CacheNames.LEADERBOARD, key = "#type + ':' + #limit", unless = "#result.isEmpty()")
    public List<BookDocument> list(String type, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));

        Criteria criteria;
        Sort sort;
        switch (type) {
            case "finished":
                criteria = new Criteria("status").in(STATUS_FINISHED);
                sort = Sort.by(Sort.Direction.DESC, FIELD_HOT_SCORE);
                break;
            case "newest":
                criteria = new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED);
                sort = Sort.by(Sort.Direction.DESC, FIELD_UPDATE_TIME);
                break;
            case "top-rated":
                criteria = new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED)
                        .and(new Criteria("ratingCount").greaterThanEqual(MIN_REVIEWS));
                sort = Sort.by(Sort.Direction.DESC, FIELD_RATING_AVG);
                break;
            case "hot":
            default:
                criteria = new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED);
                sort = Sort.by(Sort.Direction.DESC, FIELD_HOT_SCORE);
                break;
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(0, safeLimit));
        query.addSort(sort);

        SearchHits<BookDocument> hits = elasticsearchOperations.search(query, BookDocument.class);
        List<BookDocument> list = new ArrayList<>();
        for (SearchHit<BookDocument> hit : hits.getSearchHits()) {
            list.add(hit.getContent());
        }
        return list;
    }
}

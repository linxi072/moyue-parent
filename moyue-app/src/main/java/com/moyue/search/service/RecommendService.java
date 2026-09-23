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
import java.util.Comparator;
import java.util.List;

/**
 * 推荐位服务（P2-13 S-2 / P1-3 个性化）。
 *
 * <p>基础推荐位复用同一索引 {@code moyue_book}，仅取 status ∈ {1,2} 的文档，按 hotScore 降序；
 * {@code sort=latest} 时按 updateTime 降序。limit 上限受 {@code moyue.search.recommend.max-limit} 约束。</p>
 *
 * <p>P1-3 个性化：{@link #personalizeRecommend(Long, int)} 在基础召回之上，按用户兴趣画像
 * （类目 / 作者偏好）对候选重排、排除已在书架的书籍；画像为空（新用户 / 服务降级）时回退热门推荐。</p>
 */
@Service
public class RecommendService {

    /** 仅参与检索的字段状态白名单：1 连载中 / 2 已完结 */
    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_FINISHED = 2;

    private static final String FIELD_HOT_SCORE = "hotScore";
    private static final String FIELD_UPDATE_TIME = "updateTime";

    /** 个性化重排：类目命中加权（相对 hotScore 的加法偏移，确保偏好项上浮） */
    private static final long CATEGORY_BOOST = 1_000_000L;
    /** 个性化重排：作者命中加权（作者信号强于类目） */
    private static final long AUTHOR_BOOST = 2_000_000L;
    /** 个性化召回池放大倍数（相对 limit，留出重排余量） */
    private static final int POOL_FACTOR = 3;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private SearchProperties searchProperties;

    @Autowired(required = false)
    private UserProfileService userProfileService;

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
        return recallByHot(safeLimit, resolveSort(sort));
    }

    /**
     * 个性化推荐：按用户兴趣画像重排候选。
     *
     * @param userId 用户 ID（null → 退化为热门推荐）
     * @param limit  期望条数（默认 10，上限 {@code recommend.max-limit}）
     * @return 个性化推荐书籍列表；ES 不可用时回退热门推荐
     */
    public List<BookDocument> personalizeRecommend(Long userId, int limit) {
        int maxLimit = Math.max(searchProperties.getRecommend().getMaxLimit(), 1);
        int safeLimit = Math.min(Math.max(limit, 1), maxLimit);

        UserInterestProfile profile = (userId != null && userProfileService != null)
                ? userProfileService.buildProfile(userId) : UserInterestProfile.EMPTY;

        // 冷启动：无画像（新用户 / 降级）→ 直接热门推荐
        if (profile == null || profile.isEmpty()) {
            return recommend(safeLimit, "hot");
        }

        int poolSize = Math.min(safeLimit * POOL_FACTOR, maxLimit * POOL_FACTOR);
        List<BookDocument> candidates;
        try {
            candidates = recallByHot(poolSize, Sort.by(Sort.Direction.DESC, FIELD_HOT_SCORE));
        } catch (Exception ex) {
            // ES 不可用：回退热门推荐，保证推荐位始终有结果
            return recommend(safeLimit, "hot");
        }

        List<BookDocument> result = new ArrayList<>();
        for (BookDocument doc : candidates) {
            if (profile.getShelfBookIds().contains(doc.getBookId())) {
                continue; // 已书架书籍不再推荐
            }
            long affinity = affinityScore(doc, profile);
            doc.setHotScore(doc.getHotScore() + affinity); // 复用 hotScore 字段承载重排分（仅内存，不改索引）
            result.add(doc);
            if (result.size() >= safeLimit) {
                break;
            }
        }
        // 候选池去重后不足 limit：补足热门（不超过上限）
        if (result.size() < safeLimit) {
            for (BookDocument doc : candidates) {
                if (result.contains(doc) || profile.getShelfBookIds().contains(doc.getBookId())) {
                    continue;
                }
                result.add(doc);
                if (result.size() >= safeLimit) {
                    break;
                }
            }
        }
        result.sort(Comparator.comparingLong(BookDocument::getHotScore).reversed());
        return result;
    }

    /** 画像亲和度得分：类目命中 + 作者命中（权重叠加） */
    private long affinityScore(BookDocument doc, UserInterestProfile profile) {
        long score = 0L;
        if (doc.getCategoryName() != null) {
            score += CATEGORY_BOOST * profile.getCategoryWeights().getOrDefault(doc.getCategoryName(), 0);
        }
        if (doc.getAuthorName() != null) {
            score += AUTHOR_BOOST * profile.getAuthorWeights().getOrDefault(doc.getAuthorName(), 0);
        }
        return score;
    }

    /** 按排序从 ES 召回候选（status ∈ {1,2}） */
    private List<BookDocument> recallByHot(int size, Sort sort) {
        Criteria criteria = new Criteria("status").in(STATUS_ONGOING, STATUS_FINISHED);
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(0, size));
        query.addSort(sort);

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

package com.moyue.search.service;

import com.moyue.common.cache.CacheNames;
import com.moyue.common.cache.LocalCacheTestConfig;
import com.moyue.search.document.BookDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 榜单「过滤条件 + 排序」正确性验证（QA 独立补充测试）。
 *
 * <p>复用 LeaderboardServiceCacheTest 的范式：{@link LocalCacheTestConfig} 注入真实本地
 * CacheManager；{@link MockBean} 桩 ElasticsearchOperations。用 ArgumentCaptor 捕获实际下发给
 * ES 的 CriteriaQuery，并对过滤条件（status / ratingCount）与排序字段做<b>结构化断言</b>
 * （遍历 Criteria 的 field / CriteriaEntry / OperationKey，而非仅 toString 子串），从而独立证明四榜的下发查询符合预期。</p>
 *
 * <ul>
 *   <li>finished：status 仅含 {2}（已完结）；排序 hotScore DESC；</li>
 *   <li>top-rated：status ∈ {1,2} 且 ratingCount ≥ 3（MIN_REVIEWS）；排序 ratingAvg DESC；</li>
 *   <li>hot：status ∈ {1,2}；排序 hotScore DESC；</li>
 *   <li>newest：status ∈ {1,2}；排序 updateTime DESC。</li>
 * </ul>
 *
 * <p>Spring Data ES 5.2.12 的 Criteria 结构化接口为：
 * 节点字段 {@code getField().getName()}、本节点运算 {@code getQueryCriteriaEntries()}（{@link Criteria.CriteriaEntry}，
 * 其 {@code getKey()} 为 {@link Criteria.OperationKey}，{@code getValue()} 为运算值）、嵌套条件
 * {@code getSubCriteria()}。本测试据此断言。</p>
 *
 * H2 + mock ES，profile={@code test}。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalCacheTestConfig.class)
class LeaderboardServiceCriteriaTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    /** 每个测试前清空榜单缓存并 reset ES mock，确保冷启动 */
    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache(CacheNames.LEADERBOARD);
        if (cache != null) {
            cache.clear();
        }
        reset(elasticsearchOperations);
    }

    private BookDocument doc(long bookId, long hotScore) {
        BookDocument d = new BookDocument();
        d.setBookId(bookId);
        d.setStatus(1);
        d.setHotScore(hotScore);
        return d;
    }

    private SearchHits<BookDocument> hitsOf(BookDocument... docs) {
        SearchHits<BookDocument> hits = mock(SearchHits.class);
        SearchHit<BookDocument>[] arr = new SearchHit[docs.length];
        for (int i = 0; i < docs.length; i++) {
            SearchHit<BookDocument> h = mock(SearchHit.class);
            when(h.getContent()).thenReturn(docs[i]);
            arr[i] = h;
        }
        when(hits.getSearchHits()).thenReturn(Arrays.asList(arr));
        return hits;
    }

    /** 调用一次 list(type,10) 并捕获实际下发给 ES 的 CriteriaQuery */
    private CriteriaQuery captureQuery(String type) {
        SearchHits<BookDocument> hits = hitsOf(doc(1L, 100L));
        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(BookDocument.class))).thenReturn(hits);
        leaderboardService.list(type, 10);
        org.mockito.ArgumentCaptor<CriteriaQuery> captor = forClass(CriteriaQuery.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(BookDocument.class));
        return captor.getValue();
    }

    /**
     * 收集 Criteria 树中所有可断言的节点：
     * Spring Data ES 5.2 中，{@code .and(...)} 产生的同级条件挂在 {@link Criteria#getCriteriaChain()}（peer 节点），
     * 更深一层分组挂在 {@link Criteria#getSubCriteria()}。这里取 root 的 chain 全量（已含 root 自身与所有 peer），
     * 并补充各 chain 节点的 subCriteria，避免遗漏。
     */
    private List<Criteria> allNodes(Criteria root) {
        List<Criteria> nodes = new ArrayList<>();
        List<Criteria> chain = root.getCriteriaChain();
        if (chain != null && !chain.isEmpty()) {
            nodes.addAll(chain);
        } else {
            nodes.add(root);
        }
        for (Criteria n : new ArrayList<>(nodes)) {
            if (n.getSubCriteria() != null) {
                for (Criteria sub : n.getSubCriteria()) {
                    if (!nodes.contains(sub)) {
                        nodes.add(sub);
                    }
                }
            }
        }
        return nodes;
    }

    /** 在 Criteria 树中按字段名查找节点（覆盖 chain 与 subCriteria） */
    private Criteria findField(Criteria root, String fieldName) {
        for (Criteria n : allNodes(root)) {
            if (n.getField() != null && fieldName.equals(n.getField().getName())) {
                return n;
            }
        }
        return null;
    }

    /** 将 CriteriaEntry 的 value（可能是数组 / 集合 / 单值）统一转为 List<Object> */
    private List<Object> valueAsList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Object[] arr) {
            return Arrays.asList(arr);
        }
        if (value instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            it.forEach(list::add);
            return list;
        }
        return List.of(value);
    }

    /** 取某字段 Criteria 上指定 OperationKey 的运算值集合（按字段节点聚合） */
    private List<Object> valuesOf(Criteria c, Criteria.OperationKey key) {
        return c.getQueryCriteriaEntries().stream()
                .filter(e -> e.getKey() == key)
                .flatMap(e -> valueAsList(e.getValue()).stream())
                .collect(Collectors.toList());
    }

    /** finished 榜：status 仅限已完结 {2}；排序 hotScore DESC */
    @Test
    void finished_只看已完结status2() {
        CriteriaQuery q = captureQuery("finished");
        Criteria status = findField(q.getCriteria(), "status");
        assertThat(status).isNotNull();
        assertThat(valuesOf(status, Criteria.OperationKey.IN)).containsExactly(2);

        assertThat(q.getSort().getOrderFor("hotScore")).isNotNull();
        assertThat(q.getSort().getOrderFor("hotScore").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /** top-rated 榜：status ∈ {1,2} 且 ratingCount ≥ 3（MIN_REVIEWS）；排序 ratingAvg DESC */
    @Test
    void topRated_限定评分人数大于等于3() {
        CriteriaQuery q = captureQuery("top-rated");
        Criteria status = findField(q.getCriteria(), "status");
        assertThat(status).isNotNull();
        assertThat(valuesOf(status, Criteria.OperationKey.IN)).containsExactlyInAnyOrder(1, 2);

        Criteria rating = findField(q.getCriteria(), "ratingCount");
        assertThat(rating).isNotNull();
        List<Object> gteVals = valuesOf(rating, Criteria.OperationKey.GREATER_EQUAL);
        // 数值比较，兼容 Integer / Long（MIN_REVIEWS=3L 传入 greaterThanEqual）
        long[] numeric = gteVals.stream().mapToLong(o -> ((Number) o).longValue()).toArray();
        assertThat(numeric).contains(3L);

        assertThat(q.getSort().getOrderFor("ratingAvg")).isNotNull();
        assertThat(q.getSort().getOrderFor("ratingAvg").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /** hot 榜：status ∈ {1,2}；排序 hotScore DESC */
    @Test
    void hot_status含连载与完结() {
        CriteriaQuery q = captureQuery("hot");
        Criteria status = findField(q.getCriteria(), "status");
        assertThat(status).isNotNull();
        assertThat(valuesOf(status, Criteria.OperationKey.IN)).containsExactlyInAnyOrder(1, 2);

        assertThat(q.getSort().getOrderFor("hotScore")).isNotNull();
        assertThat(q.getSort().getOrderFor("hotScore").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /** newest 榜：status ∈ {1,2}；排序 updateTime DESC */
    @Test
    void newest_status含连载与完结且按updateTime降序() {
        CriteriaQuery q = captureQuery("newest");
        Criteria status = findField(q.getCriteria(), "status");
        assertThat(status).isNotNull();
        assertThat(valuesOf(status, Criteria.OperationKey.IN)).containsExactlyInAnyOrder(1, 2);

        assertThat(q.getSort().getOrderFor("updateTime")).isNotNull();
        assertThat(q.getSort().getOrderFor("updateTime").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}

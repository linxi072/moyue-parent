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
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 榜单缓存命中集成测试（性能与缓存模块）。
 * 复用 {@link com.moyue.search.service.RecommendServiceCacheTest} 范式：{@link LocalCacheTestConfig}
 * 注入真实本地 CacheManager 使 {@code @Cacheable} 生效；{@link MockBean} 桩 ES 并断言调用次数，证明缓存命中。
 * 每个测试前清空 LEADERBOARD 缓存并 reset ES mock（清除桩），保证从冷缓存 + 干净桩开始。
 * H2 + mock ES，profile={@code test}。
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(LocalCacheTestConfig.class)
class LeaderboardServiceCacheTest {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    /** 每个测试前清空榜单缓存并 reset ES mock，确保冷启动（缓存跨测试方法共享于同一上下文） */
    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache(CacheNames.LEADERBOARD);
        if (cache != null) {
            cache.clear();
        }
        reset(elasticsearchOperations);
    }

    private BookDocument doc(long bookId, String title, long hot) {
        BookDocument d = new BookDocument();
        d.setBookId(bookId);
        d.setTitle(title);
        d.setStatus(1);
        d.setHotScore(hot);
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

    /** 连续两次 list("hot",10)：第二次命中缓存，ES 仅被查 1 次 */
    @Test
    void hot_缓存命中() {
        SearchHits<BookDocument> hits = hitsOf(doc(1L, "热书A", 100L), doc(2L, "热书B", 50L));
        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(BookDocument.class))).thenReturn(hits);

        List<BookDocument> r1 = leaderboardService.list("hot", 10);
        List<BookDocument> r2 = leaderboardService.list("hot", 10);

        assertThat(r1).hasSize(2);
        assertThat(r2).isEqualTo(r1);
        // 第二次命中缓存，不再查 ES
        verify(elasticsearchOperations, times(1)).search(any(CriteriaQuery.class), eq(BookDocument.class));
    }

    /** 不同 type → 不同缓存 key → ES 各查一次（共 2 次） */
    @Test
    void 不同type不同key() {
        SearchHits<BookDocument> hits = hitsOf(doc(1L, "热书A", 100L));
        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(BookDocument.class))).thenReturn(hits);

        leaderboardService.list("hot", 10);
        leaderboardService.list("newest", 10);

        verify(elasticsearchOperations, times(2)).search(any(CriteriaQuery.class), eq(BookDocument.class));
    }

    /** 排序字段正确：hot→hotScore / newest→updateTime / top-rated→ratingAvg（用 captor 捕获入参断言） */
    @Test
    void 排序字段正确() {
        SearchHits<BookDocument> hits = hitsOf(doc(1L, "书A", 100L));
        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(BookDocument.class))).thenReturn(hits);

        leaderboardService.list("hot", 10);
        leaderboardService.list("newest", 10);
        leaderboardService.list("top-rated", 10);

        org.mockito.ArgumentCaptor<CriteriaQuery> captor = forClass(CriteriaQuery.class);
        verify(elasticsearchOperations, times(3)).search(captor.capture(), eq(BookDocument.class));

        List<CriteriaQuery> queries = captor.getAllValues();
        // 调用顺序：hot → newest → top-rated，对应排序字段依次断言
        assertThat(queries.get(0).getSort().getOrderFor("hotScore")).isNotNull();
        assertThat(queries.get(1).getSort().getOrderFor("updateTime")).isNotNull();
        assertThat(queries.get(2).getSort().getOrderFor("ratingAvg")).isNotNull();
    }
}

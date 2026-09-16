package com.moyue.search.service;

import com.moyue.search.config.SearchProperties;
import com.moyue.search.document.BookDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RecommendService.personalizeRecommend 纯 Mockito 单测（项目禁用 Docker，不启 Spring）。
 * 覆盖：冷启动、画像重排+书架去重、ES 不可用兜底。
 *
 * <p>search 桩用 {@link #pageAwareHits()} 遵守 {@code PageRequest} 分页大小，
 * 以贴近 ES 真实行为（{@code recommend(limit)} 把 limit 作为页大小传给 ES）。</p>
 */
@ExtendWith(MockitoExtension.class)
class QaRecommendServicePersonalizeTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private SearchProperties searchProperties;

    @InjectMocks
    private RecommendService recommendService;

    /** 固定候选池（热度降序，bookId 固定便于去重断言） */
    private final BookDocument[] POOL = {
            doc(101L, "都市热书", "都市", "王五", 300L),
            doc(102L, "玄幻热书", "玄幻", "张三", 200L),
            doc(103L, "悬疑热书", "悬疑", "赵六", 100L),
    };

    private BookDocument doc(long bookId, String title, String category, String author, long hot) {
        BookDocument d = new BookDocument();
        d.setBookId(bookId);
        d.setTitle(title);
        d.setCategoryName(category);
        d.setAuthorName(author);
        d.setHotScore(hot);
        d.setStatus(1);
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

    /** 遵守分页大小的 search 桩：返回 POOL 的前 min(pageSize, POOL.length) 本 */
    private void stubSearchPageAware() {
        SearchProperties.Recommend recommendCfg = new SearchProperties.Recommend();
        recommendCfg.setMaxLimit(50);
        when(searchProperties.getRecommend()).thenReturn(recommendCfg);

        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(BookDocument.class)))
                .thenAnswer(inv -> {
                    CriteriaQuery q = inv.getArgument(0);
                    int size = 3;
                    Pageable p = q.getPageable();
                    if (p != null && p.isPaged()) {
                        size = p.getPageSize();
                    }
                    int n = Math.min(size, POOL.length);
                    return hitsOf(Arrays.copyOfRange(POOL, 0, n));
                });
    }

    @Test
    void userId_null_coldStart_returnsHotTopN() {
        stubSearchPageAware();
        List<BookDocument> result = recommendService.personalizeRecommend(null, 2);
        assertEquals(2, result.size());
        // 冷启动按热度：300 > 200
        assertEquals("都市热书", result.get(0).getTitle());
        assertEquals("玄幻热书", result.get(1).getTitle());
    }

    @Test
    void profileEmpty_coldStart() {
        stubSearchPageAware();
        when(userProfileService.buildProfile(1L)).thenReturn(UserInterestProfile.EMPTY);
        List<BookDocument> result = recommendService.personalizeRecommend(1L, 2);
        assertEquals(2, result.size());
        assertEquals("都市热书", result.get(0).getTitle());
    }

    @Test
    void profileReranksAndExcludesShelf() {
        stubSearchPageAware();
        // 用户偏好「玄幻」，书架含 102（玄幻热书）
        UserInterestProfile profile = UserInterestProfile.of(
                Map.of("玄幻", 1), Map.of(), Set.of(102L));
        when(userProfileService.buildProfile(9L)).thenReturn(profile);

        // 102 在书架 → 排除；剩 101 都市(300) 与 103 悬疑(100)
        List<BookDocument> result = recommendService.personalizeRecommend(9L, 2);
        assertEquals(2, result.size());
        assertEquals("都市热书", result.get(0).getTitle());
        assertEquals("悬疑热书", result.get(1).getTitle());
        assertFalse(result.stream().anyMatch(d -> "玄幻热书".equals(d.getTitle())));
    }

    @Test
    void esUnavailable_fallsBackToHot() {
        SearchProperties.Recommend recommendCfg = new SearchProperties.Recommend();
        recommendCfg.setMaxLimit(50);
        when(searchProperties.getRecommend()).thenReturn(recommendCfg);

        // 非空画像（仅书架信号，无权重）→ 个性化召回路径进入 try；ES 第一次抛异常应回退热门
        UserInterestProfile shelfOnly = UserInterestProfile.of(Map.of(), Map.of(), Set.of(999L));
        when(userProfileService.buildProfile(5L)).thenReturn(shelfOnly);

        BookDocument b1 = doc(8001L, "都市热书", "都市", "王五", 300L);
        BookDocument b2 = doc(8002L, "玄幻热书", "玄幻", "张三", 200L);
        SearchHits<BookDocument> fallbackHits = hitsOf(b1, b2);
        // 第一次 recall 抛异常（ES 宕），兜底 recommend 第二次成功
        when(elasticsearchOperations.search(any(CriteriaQuery.class), eq(BookDocument.class)))
                .thenThrow(new RuntimeException("ES down"))
                .thenReturn(fallbackHits);

        List<BookDocument> result = recommendService.personalizeRecommend(5L, 2);
        assertFalse(result.isEmpty());
        assertEquals("都市热书", result.get(0).getTitle());
    }
}

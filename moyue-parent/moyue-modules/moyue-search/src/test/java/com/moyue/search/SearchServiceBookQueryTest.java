package com.moyue.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.search.document.BookDocument;
import com.moyue.search.repository.BookSearchRepository;
import com.moyue.search.service.SearchService;
import com.moyue.search.service.SynonymExpander;
import com.moyue.search.service.SpellCorrector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchService.search（书籍检索 NativeQuery 迁移）回归测试：
 * 重点回归 S1——status 白名单 {1,2} 必须为 OR 语义（filter 内嵌 should bool），
 * 不允许出现两个并列 term filter（AND 互斥导致检索恒空）；同时回归 sort 行为不变
 * 与 multi_match 字段权重。
 */
class SearchServiceBookQueryTest {

    @SuppressWarnings("unchecked")
    private final SearchHits<BookDocument> hits = mock(SearchHits.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);

    private final SearchService service = createService();

    private SearchService createService() {
        SearchService s = new SearchService();
        ReflectionTestUtils.setField(s, "bookSearchRepository", mock(BookSearchRepository.class));
        ReflectionTestUtils.setField(s, "elasticsearchOperations", operations);
        ReflectionTestUtils.setField(s, "searchProperties", new com.moyue.search.config.SearchProperties());
        // P1-5：search() 依赖同义词扩展；此处透传原词，保持字段权重 / status 白名单回归不变
        SynonymExpander synonymExpander = mock(SynonymExpander.class);
        SpellCorrector spellCorrector = mock(SpellCorrector.class);
        when(synonymExpander.expand(any())).thenAnswer(inv -> Set.of((String) inv.getArgument(0)));
        ReflectionTestUtils.setField(s, "synonymExpander", synonymExpander);
        ReflectionTestUtils.setField(s, "spellCorrector", spellCorrector);
        return s;
    }

    private JsonNode lastQueryJson() throws Exception {
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, org.mockito.Mockito.atLeastOnce()).search(captor.capture(), eq(BookDocument.class));
        var lastQuery = captor.getAllValues().get(captor.getAllValues().size() - 1);
        String json = JsonpUtils.toJsonString(lastQuery.getQuery(), new JacksonJsonpMapper());
        return new ObjectMapper().readTree(json);
    }

    private void stubSearch() {
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(BookDocument.class))).thenReturn(hits);
    }

    @Test
    @DisplayName("回归 S1：status 白名单 {1,2} 为 filter 内嵌 should bool（OR 语义），非并列 term filter")
    void searchShouldUseShouldBoolForStatusWhitelist() throws Exception {
        stubSearch();
        service.search("剑来", null, "relevance", 1, 10);
        JsonNode json = lastQueryJson();

        JsonNode filters = json.path("bool").path("filter");
        boolean foundOrFilter = false;
        for (JsonNode f : filters) {
            if (f.has("bool")) {
                JsonNode inner = f.get("bool");
                List<String> statusTerms = new java.util.ArrayList<>();
                inner.path("should").forEach(sh -> {
                    if (sh.path("term").has("status")) {
                        statusTerms.add(sh.path("term").path("status").path("value").asText());
                    }
                });
                if (!statusTerms.isEmpty()) {
                    assertThat(statusTerms).containsExactlyInAnyOrder("1", "2");
                    assertThat(inner.path("minimum_should_match").asText()).isEqualTo("1");
                    foundOrFilter = true;
                }
            }
            // 并列的 status term filter（AND 互斥，S1 错误写法）不允许再出现
            assertThat(f.path("term").has("status")).isFalse();
        }
        assertThat(foundOrFilter).as("filter 内必须存在 status OR 过滤（should bool）").isTrue();
    }

    @Test
    @DisplayName("multi_match：title^3 / authorName^2 / categoryName / description，minimumShouldMatch 75%")
    void searchShouldBuildWeightedMultiMatch() throws Exception {
        stubSearch();
        service.search("剑来", null, "relevance", 1, 10);
        JsonNode json = lastQueryJson();

        JsonNode multiMatch = json.path("bool").path("must").get(0).path("bool").path("should").get(0).path("multi_match");
        assertThat(multiMatch.path("query").asText()).isEqualTo("剑来");
        List<String> fields = new java.util.ArrayList<>();
        multiMatch.path("fields").forEach(f -> fields.add(f.asText()));
        assertThat(fields).containsExactlyInAnyOrder(
                "title^3", "authorName^2", "categoryName", "description");
        assertThat(multiMatch.path("minimum_should_match").asText()).isEqualTo("75%");
    }

    @Test
    @DisplayName("categoryId 非空 → filter context term 过滤；为空 → 无该过滤")
    void searchShouldFilterCategoryIdOnlyWhenPresent() throws Exception {
        stubSearch();
        service.search("剑来", 5L, "relevance", 1, 10);
        JsonNode json = lastQueryJson();
        assertThat(json.path("bool").path("filter").toString())
                .contains("\"term\":{\"categoryId\":{\"value\":5}}");

        stubSearch(); // 重置捕获后再次执行（bookId 为空分支）
        service.search("剑来", null, "relevance", 1, 10);
        JsonNode json2 = lastQueryJson();
        assertThat(json2.path("bool").path("filter").toString()).doesNotContain("categoryId");
    }

    @Test
    @DisplayName("sort 行为不变：hot→hotScore desc / latest→updateTime desc / relevance 与非法值不排序")
    void searchShouldApplySortAsBefore() {
        stubSearch();
        service.search("剑来", null, "hot", 1, 10);
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, org.mockito.Mockito.times(1)).search(captor.capture(), eq(BookDocument.class));
        List<SortOptions> sorts = captor.getValue().getSortOptions();
        assertThat(sorts).hasSize(1);
        assertThat(sorts.get(0).field().field()).isEqualTo("hotScore");
        assertThat(sorts.get(0).field().order()).isEqualTo(SortOrder.Desc);

        ArgumentCaptor<NativeQuery> captor2 = ArgumentCaptor.forClass(NativeQuery.class);
        service.search("剑来", null, "latest", 1, 10);
        verify(operations, org.mockito.Mockito.times(2)).search(captor2.capture(), eq(BookDocument.class));
        List<SortOptions> sorts2 = captor2.getAllValues().get(1).getSortOptions();
        assertThat(sorts2).hasSize(1);
        assertThat(sorts2.get(0).field().field()).isEqualTo("updateTime");
        assertThat(sorts2.get(0).field().order()).isEqualTo(SortOrder.Desc);

        ArgumentCaptor<NativeQuery> captor3 = ArgumentCaptor.forClass(NativeQuery.class);
        service.search("剑来", null, "relevance", 1, 10);
        verify(operations, org.mockito.Mockito.times(3)).search(captor3.capture(), eq(BookDocument.class));
        assertThat(captor3.getAllValues().get(2).getSortOptions()).isEmpty();

        ArgumentCaptor<NativeQuery> captor4 = ArgumentCaptor.forClass(NativeQuery.class);
        service.search("剑来", null, "非法值", 1, 10); // 回退 relevance
        verify(operations, org.mockito.Mockito.times(4)).search(captor4.capture(), eq(BookDocument.class));
        assertThat(captor4.getAllValues().get(3).getSortOptions()).isEmpty();
    }

    @Test
    @DisplayName("执行与映射：SearchHits → PageResult（total/page/size/records 原文档透传）")
    void searchShouldMapHitsToPageResult() {
        BookDocument doc = new BookDocument();
        doc.setBookId(1L);
        doc.setTitle("凡人修仙传");
        doc.setStatus(2);

        @SuppressWarnings("unchecked")
        SearchHit<BookDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(operations.search(any(NativeQuery.class), eq(BookDocument.class))).thenReturn(hits);

        com.moyue.common.core.domain.PageResult<BookDocument> result =
                service.search("凡人", null, "relevance", 0, 0);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTitle()).isEqualTo("凡人修仙传");
    }
}

package com.moyue.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.dto.ChapterSearchResultDTO;
import com.moyue.search.repository.ChapterSearchRepository;
import com.moyue.search.service.ChapterSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChapterSearchService 单测（Mockito 隔离 ElasticsearchOperations，ES 本机不可用）：
 * 覆盖索引写入 / 删除、multi_match + filter context 查询构造（JSON 断言）、
 * bookId 两分支、高亮参数、分页归一化与结果映射。
 */
class ChapterSearchServiceTest {

    private final ChapterSearchRepository repository = mock(ChapterSearchRepository.class);
    @SuppressWarnings("unchecked")
    private final SearchHits<ChapterDocument> hits = mock(SearchHits.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);

    private final ChapterSearchService service = createService();

    private ChapterSearchService createService() {
        ChapterSearchService s = new ChapterSearchService();
        ReflectionTestUtils.setField(s, "chapterSearchRepository", repository);
        ReflectionTestUtils.setField(s, "elasticsearchOperations", operations);
        ReflectionTestUtils.setField(s, "searchProperties", new com.moyue.search.config.SearchProperties());
        return s;
    }

    /** NativeQuery 内部 ES DSL → JSON 树（便于结构化断言 filter / multi_match） */
    private static JsonNode queryJson(NativeQuery query) throws Exception {
        Query esQuery = query.getQuery();
        String json = JsonpUtils.toJsonString(esQuery, new JacksonJsonpMapper());
        return new ObjectMapper().readTree(json);
    }

    private void stubSearch(List<SearchHit<ChapterDocument>> hitList, long total) {
        when(hits.getTotalHits()).thenReturn(total);
        when(hits.getSearchHits()).thenReturn(hitList);
        when(operations.search(any(NativeQuery.class), eq(ChapterDocument.class))).thenReturn(hits);
    }

    @Test
    @DisplayName("索引一章：repository.save 覆盖写入（幂等），返回原文档")
    void indexShouldSaveDocument() {
        ChapterDocument doc = new ChapterDocument();
        doc.setChapterId(1L);
        when(repository.save(doc)).thenReturn(doc);

        ChapterDocument saved = service.index(doc);

        assertThat(saved).isSameAs(doc);
        verify(repository).save(doc);
    }

    @Test
    @DisplayName("章节删除 / 下架同步：deleteById 物理删除")
    void removeShouldDeleteById() {
        service.remove(9L);
        verify(repository).deleteById(9L);
    }

    @Test
    @DisplayName("检索：multi_match(chapterTitle^2 + content) 入 must，status=2 入 filter context")
    void searchShouldBuildMultiMatchWithStatusFilter() throws Exception {
        stubSearch(List.of(), 0L);

        service.search("剑来", null, 1, 10);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ChapterDocument.class));
        JsonNode json = queryJson(captor.getValue());

        JsonNode multiMatch = json.path("bool").path("must").get(0).path("multi_match");
        assertThat(multiMatch.path("query").asText()).isEqualTo("剑来");
        List<String> fields = new java.util.ArrayList<>();
        multiMatch.path("fields").forEach(f -> fields.add(f.asText()));
        assertThat(fields).containsExactlyInAnyOrder("chapterTitle^2", "content");

        // status 过滤必须在 filter context（不计分 + 可缓存）
        JsonNode filters = json.path("bool").path("filter");
        assertThat(filters.toString()).contains("\"term\":{\"status\":{\"value\":2}}");
    }

    @Test
    @DisplayName("检索 bookId 非空：bookId term 过滤入 filter context")
    void searchShouldFilterByBookIdWhenPresent() throws Exception {
        stubSearch(List.of(), 0L);

        service.search("剑来", 5L, 1, 10);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ChapterDocument.class));
        JsonNode filters = queryJson(captor.getValue()).path("bool").path("filter");
        assertThat(filters.toString()).contains("\"term\":{\"bookId\":{\"value\":5}}");
    }

    @Test
    @DisplayName("检索 bookId 为空：不加 bookId 过滤（两分支覆盖）")
    void searchShouldNotFilterByBookIdWhenNull() throws Exception {
        stubSearch(List.of(), 0L);

        service.search("剑来", null, 1, 10);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ChapterDocument.class));
        JsonNode filters = queryJson(captor.getValue()).path("bool").path("filter");
        assertThat(filters.toString()).doesNotContain("bookId");
    }

    @Test
    @DisplayName("高亮：content 字段 <em> 标签，fragmentSize=150 / noMatchSize=100")
    void searchShouldConfigureContentHighlight() throws Exception {
        stubSearch(List.of(), 0L);

        service.search("剑来", null, 1, 10);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ChapterDocument.class));
        HighlightQuery highlightQuery = captor.getValue().getHighlightQuery()
                .orElseThrow(() -> new AssertionError("应配置 highlight query"));
        Highlight highlight = highlightQuery.getHighlight();
        List<HighlightField> fields = highlight.getFields();
        assertThat(fields).hasSize(1);
        HighlightField contentField = fields.get(0);
        assertThat(contentField.getName()).isEqualTo("content");
        assertThat(contentField.getParameters().getPreTags()).containsExactly("<em>");
        assertThat(contentField.getParameters().getPostTags()).containsExactly("</em>");
        assertThat(contentField.getParameters().getFragmentSize()).isEqualTo(150);
        assertThat(contentField.getParameters().getNoMatchSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("分页归一化：page<1 按 1 处理，size<=0 取配置默认值 20")
    void searchShouldNormalizePageAndSize() throws Exception {
        stubSearch(List.of(), 0L);

        service.search("剑来", null, 0, 0);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ChapterDocument.class));
        Pageable pageable = captor.getValue().getPageable();
        assertThat(pageable).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("结果映射：文档元信息 + 正文高亮片段转入 ChapterSearchResultDTO")
    void searchShouldMapHitsToResultDto() {
        ChapterDocument doc = new ChapterDocument();
        doc.setChapterId(11L);
        doc.setBookId(22L);
        doc.setBookTitle("凡人修仙传");
        doc.setChapterTitle("第一章");
        doc.setStatus(2);
        doc.setPublishTime(new Date(1700000000000L));

        @SuppressWarnings("unchecked")
        SearchHit<ChapterDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightField("content")).thenReturn(List.of("<em>韩立</em>盘膝而坐"));
        stubSearch(List.of(hit), 1L);

        com.moyue.common.core.domain.PageResult<ChapterSearchResultDTO> result =
                service.search("韩立", null, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getRecords()).hasSize(1);
        ChapterSearchResultDTO dto = result.getRecords().get(0);
        assertThat(dto.getChapterId()).isEqualTo(11L);
        assertThat(dto.getBookId()).isEqualTo(22L);
        assertThat(dto.getBookTitle()).isEqualTo("凡人修仙传");
        assertThat(dto.getChapterTitle()).isEqualTo("第一章");
        assertThat(dto.getStatus()).isEqualTo(2);
        assertThat(dto.getPublishTime()).isEqualTo(new Date(1700000000000L));
        assertThat(dto.getHighlights()).containsExactly("<em>韩立</em>盘膝而坐");
    }

    @Test
    @DisplayName("仅标题命中：正文无高亮片段时 highlights 为空列表（非 null）")
    void searchShouldMapEmptyHighlightsWhenTitleOnlyHit() {
        ChapterDocument doc = new ChapterDocument();
        doc.setChapterId(1L);
        doc.setStatus(2);

        @SuppressWarnings("unchecked")
        SearchHit<ChapterDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightField("content")).thenReturn(List.of());
        stubSearch(List.of(hit), 1L);

        com.moyue.common.core.domain.PageResult<ChapterSearchResultDTO> result =
                service.search("第一章", null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getHighlights()).isNotNull().isEmpty();
    }
}

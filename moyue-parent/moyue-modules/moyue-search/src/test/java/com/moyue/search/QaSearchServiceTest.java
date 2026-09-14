package com.moyue.search;

import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.document.QaDocument;
import com.moyue.search.repository.QaSearchRepository;
import com.moyue.search.service.QaSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QaSearchService 单测（Mockito 隔离 ElasticsearchOperations）：
 * 覆盖索引写入 / 删除 / 按会话删除、question+answer 匹配与 createTime 时间范围
 * filter 分支、分页归一化与结果映射。
 */
class QaSearchServiceTest {

    private final QaSearchRepository repository = mock(QaSearchRepository.class);
    @SuppressWarnings("unchecked")
    private final SearchHits<QaDocument> hits = mock(SearchHits.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);

    private final QaSearchService service = createService();

    private QaSearchService createService() {
        QaSearchService s = new QaSearchService();
        ReflectionTestUtils.setField(s, "qaSearchRepository", repository);
        ReflectionTestUtils.setField(s, "elasticsearchOperations", operations);
        ReflectionTestUtils.setField(s, "searchProperties", new SearchProperties());
        return s;
    }

    /** 展平 Criteria 链为「字段名:操作键」列表（时间条件值收集到 values，仅 Date） */
    private static List<String> flatten(Criteria criteria, List<Object> values) {
        List<String> entries = new ArrayList<>();
        List<Criteria> chain = criteria.getCriteriaChain();
        List<Criteria> all = chain.isEmpty() ? List.of(criteria) : chain;
        if (!chain.isEmpty() && !all.contains(criteria)) {
            all.add(criteria);
        }
        for (Criteria c : all) {
            String field = c.getField() == null ? "?" : c.getField().getName();
            Set<Criteria.CriteriaEntry> es = new LinkedHashSet<>(c.getQueryCriteriaEntries());
            es.addAll(c.getFilterCriteriaEntries());
            for (Criteria.CriteriaEntry entry : es) {
                entries.add(field + ":" + entry.getKey());
                if (entry.getValue() instanceof Date) {
                    values.add(entry.getValue());
                }
            }
        }
        return entries;
    }

    @Test
    @DisplayName("索引一轮问答：repository.save 覆盖写入（幂等）")
    void indexShouldSaveDocument() {
        QaDocument doc = new QaDocument();
        doc.setMessageId(1L);
        when(repository.save(doc)).thenReturn(doc);

        assertThat(service.index(doc)).isSameAs(doc);
        verify(repository).save(doc);
    }

    @Test
    @DisplayName("删除单条问答：deleteById")
    void removeShouldDeleteById() {
        service.remove(3L);
        verify(repository).deleteById(3L);
    }

    @Test
    @DisplayName("按会话删除：sessionId term 删除查询")
    void removeBySessionShouldBuildTermDeleteQuery() {
        service.removeBySession(7L);

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).delete(captor.capture(), eq(QaDocument.class));
        String json = JsonpUtils.toString(captor.getValue().getQuery());
        assertThat(json).contains("\"term\":{\"sessionId\":{\"value\":7}}");
    }

    @Test
    @DisplayName("检索：question / answer 任一 match，无时间范围时不含 createTime 条件")
    void searchShouldMatchQuestionOrAnswer() {
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(CriteriaQuery.class), eq(QaDocument.class))).thenReturn(hits);

        service.search("退款", null, null, 1, 10);

        ArgumentCaptor<CriteriaQuery> captor = ArgumentCaptor.forClass(CriteriaQuery.class);
        verify(operations).search(captor.capture(), eq(QaDocument.class));
        List<Object> values = new ArrayList<>();
        List<String> entries = flatten(captor.getValue().getCriteria(), values);
        assertThat(entries).containsExactlyInAnyOrder(
                "question:MATCHES", "answer:MATCHES");
    }

    @Test
    @DisplayName("检索时间范围：startTime/endTime → createTime GREATER_EQUAL / LESS_EQUAL")
    void searchShouldApplyTimeRangeFilter() {
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(CriteriaQuery.class), eq(QaDocument.class))).thenReturn(hits);

        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        service.search("退款", start, end, 1, 10);

        ArgumentCaptor<CriteriaQuery> captor = ArgumentCaptor.forClass(CriteriaQuery.class);
        verify(operations).search(captor.capture(), eq(QaDocument.class));
        List<Object> values = new ArrayList<>();
        List<String> entries = flatten(captor.getValue().getCriteria(), values);
        assertThat(entries).contains("createTime:GREATER_EQUAL", "createTime:LESS_EQUAL");
        assertThat(values).contains(
                Date.from(start.atZone(java.time.ZoneId.systemDefault()).toInstant()),
                Date.from(end.atZone(java.time.ZoneId.systemDefault()).toInstant()));
    }

    @Test
    @DisplayName("检索仅传 endTime：只有 LESS_EQUAL 条件（边界分支）")
    void searchShouldApplyEndTimeOnly() {
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(CriteriaQuery.class), eq(QaDocument.class))).thenReturn(hits);

        service.search("退款", null, LocalDateTime.of(2026, 1, 1, 0, 0), 1, 10);

        ArgumentCaptor<CriteriaQuery> captor = ArgumentCaptor.forClass(CriteriaQuery.class);
        verify(operations).search(captor.capture(), eq(QaDocument.class));
        List<Object> values = new ArrayList<>();
        List<String> entries = flatten(captor.getValue().getCriteria(), values);
        assertThat(entries).contains("createTime:LESS_EQUAL")
                .doesNotContain("createTime:GREATER_EQUAL");
    }

    @Test
    @DisplayName("分页归一化：page<1 按 1 处理，size<=0 取配置默认值 20")
    void searchShouldNormalizePageAndSize() {
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(CriteriaQuery.class), eq(QaDocument.class))).thenReturn(hits);

        service.search("退款", null, null, 0, -5);

        ArgumentCaptor<CriteriaQuery> captor = ArgumentCaptor.forClass(CriteriaQuery.class);
        verify(operations).search(captor.capture(), eq(QaDocument.class));
        assertThat(captor.getValue().getPageable().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getPageable().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("结果映射：SearchHits → PageResult（total/page/size/records）")
    void searchShouldMapHitsToPageResult() {
        QaDocument doc = new QaDocument();
        doc.setMessageId(9L);
        doc.setQuestion("怎么退款");
        doc.setAnswer("在订单页操作");

        @SuppressWarnings("unchecked")
        SearchHit<QaDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(operations.search(any(CriteriaQuery.class), eq(QaDocument.class))).thenReturn(hits);

        com.moyue.common.core.domain.PageResult<QaDocument> result =
                service.search("退款", null, null, 2, 5);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getQuestion()).isEqualTo("怎么退款");
    }
}

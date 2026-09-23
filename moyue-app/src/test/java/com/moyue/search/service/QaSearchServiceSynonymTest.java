package com.moyue.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.moyue.common.core.domain.PageResult;
import com.moyue.search.config.SearchProperties;
import com.moyue.search.document.BookDocument;
import com.moyue.search.dto.BookSearchResult;
import com.moyue.search.repository.BookSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchService 同义词扩展 / 纠错接线单测（纯 Mockito，不启 Spring / ES）。
 */
@ExtendWith(MockitoExtension.class)
class QaSearchServiceSynonymTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SearchProperties searchProperties;

    @Mock
    private SynonymExpander synonymExpander;

    @Mock
    private SpellCorrector spellCorrector;

    @Mock
    @SuppressWarnings("unused")
    private BookSearchRepository bookSearchRepository;

    private SearchService searchService;

    @BeforeEach
    void wire() {
        searchService = new SearchService();
        ReflectionTestUtils.setField(searchService, "elasticsearchOperations", elasticsearchOperations);
        ReflectionTestUtils.setField(searchService, "searchProperties", searchProperties);
        ReflectionTestUtils.setField(searchService, "synonymExpander", synonymExpander);
        ReflectionTestUtils.setField(searchService, "spellCorrector", spellCorrector);
        ReflectionTestUtils.setField(searchService, "bookSearchRepository", bookSearchRepository);
    }

    private BookDocument doc(long bookId, String title) {
        BookDocument d = new BookDocument();
        d.setBookId(bookId);
        d.setTitle(title);
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
        when(hits.getSearchHits()).thenReturn(List.of(arr));
        when(hits.getTotalHits()).thenReturn((long) docs.length);
        return hits;
    }

    @Test
    void buildBookQuery_returnsNonNull() {
        Query q = searchService.buildBookQuery(Set.of("玄幻", "修真"), 5L);
        assertNotNull(q);
    }

    @Test
    void search_expandsSynonymsAndInvokesExpander() {
        when(synonymExpander.expand("玄幻")).thenReturn(Set.of("玄幻", "修真"));
        SearchHits<BookDocument> primaryHits = hitsOf(doc(1L, "玄幻小说"));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(BookDocument.class)))
                .thenReturn(primaryHits);

        PageResult<BookDocument> r = searchService.search("玄幻", null, "relevance", 1, 20);
        verify(synonymExpander).expand("玄幻");
        assertEquals(1, r.getRecords().size());
    }

    @Test
    void searchWithCorrection_retriesOnEmptyAndSetsCorrectedKeyword() {
        when(synonymExpander.expand(anyString())).thenReturn(Set.of("玄幻"));
        when(spellCorrector.correct("玄幼")).thenReturn("玄幻");

        SearchHits<BookDocument> empty = mock(SearchHits.class);
        when(empty.getTotalHits()).thenReturn(0L);
        when(empty.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(BookDocument.class)))
                .thenReturn(empty)                // 主检索「玄幼」无命中
                .thenReturn(hitsOf(doc(1L, "玄幻小说"))); // 纠错「玄幻」命中

        BookSearchResult r = searchService.searchWithCorrection("玄幼", null, "relevance", 1, 20);
        assertEquals("玄幻", r.getCorrectedKeyword());
        assertEquals(1, r.getRecords().size());
    }

    @Test
    void searchWithCorrection_noHit_noCorrection() {
        when(synonymExpander.expand(anyString())).thenReturn(Set.of("玄幻"));
        when(spellCorrector.correct("zzz")).thenReturn(null);

        SearchHits<BookDocument> empty = mock(SearchHits.class);
        when(empty.getTotalHits()).thenReturn(0L);
        when(empty.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(BookDocument.class))).thenReturn(empty);

        BookSearchResult r = searchService.searchWithCorrection("zzz", null, "relevance", 1, 20);
        assertNull(r.getCorrectedKeyword());
        assertEquals(0, r.getRecords().size());
    }
}

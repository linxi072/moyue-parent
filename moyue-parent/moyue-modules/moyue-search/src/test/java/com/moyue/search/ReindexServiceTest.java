package com.moyue.search;

import com.moyue.api.ai.client.AiClient;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.ChapterClient;
import com.moyue.api.search.dto.BookIndexDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.search.document.ChapterDocument;
import com.moyue.search.document.QaDocument;
import com.moyue.search.repository.ChapterSearchRepository;
import com.moyue.search.repository.QaSearchRepository;
import com.moyue.search.service.ReindexService;
import com.moyue.search.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReindexService 单测（Mockito 隔离 Feign client 与 ES repository）：
 * 覆盖分页拉取-写入循环、10000 页护栏、下游降级（R 失败）中止报错、
 * 空 DTO 容错与幂等重跑。
 */
class ReindexServiceTest {

    private final BookClient bookClient = mock(BookClient.class);
    private final ChapterClient chapterClient = mock(ChapterClient.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final SearchService searchService = mock(SearchService.class);
    private final ChapterSearchRepository chapterRepository = mock(ChapterSearchRepository.class);
    private final QaSearchRepository qaRepository = mock(QaSearchRepository.class);

    private final ReindexService service = createService();

    private ReindexService createService() {
        ReindexService s = new ReindexService();
        ReflectionTestUtils.setField(s, "bookClient", bookClient);
        ReflectionTestUtils.setField(s, "chapterClient", chapterClient);
        ReflectionTestUtils.setField(s, "aiClient", aiClient);
        ReflectionTestUtils.setField(s, "searchService", searchService);
        ReflectionTestUtils.setField(s, "chapterSearchRepository", chapterRepository);
        ReflectionTestUtils.setField(s, "qaSearchRepository", qaRepository);
        return s;
    }

    private static BookIndexDTO book(long id) {
        BookIndexDTO dto = new BookIndexDTO();
        dto.setBookId(id);
        dto.setTitle("书" + id);
        dto.setStatus(2);
        return dto;
    }

    private static ChapterIndexDTO chapter(long id) {
        ChapterIndexDTO dto = new ChapterIndexDTO();
        dto.setChapterId(id);
        dto.setBookId(100L);
        dto.setChapterTitle("章节" + id);
        dto.setContent("正文");
        dto.setStatus(ChapterIndexDTO.STATUS_PUBLISHED);
        return dto;
    }

    private static QaIndexDTO qa(long id) {
        QaIndexDTO dto = new QaIndexDTO();
        dto.setMessageId(id);
        dto.setSessionId(1L);
        dto.setQuestion("问" + id);
        dto.setAnswer("答" + id);
        return dto;
    }

    private static <T> PageResult<T> pageOf(List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.setRecords(records);
        p.setTotal(records.size());
        p.setPage(1);
        p.setSize(records.size());
        return p;
    }

    // ------------------------------ book ------------------------------

    @Test
    @DisplayName("book 重建：单页 3 条（<100）→ 写入 3 条后停止，返回 3")
    void reindexBookShouldWritePageAndStopOnPartialPage() {
        when(bookClient.pageBooks(eq(1), eq(100)))
                .thenReturn(R.ok(pageOf(List.of(book(1), book(2), book(3)))));

        long total = service.reindex("book");

        assertThat(total).isEqualTo(3);
        verify(searchService, times(3)).index(any());
        verify(bookClient, times(1)).pageBooks(anyInt(), anyInt());
    }

    @Test
    @DisplayName("book 重建：整页 100 条触发翻页，下页 50 条（共 150）")
    void reindexBookShouldPaginateUntilPartialPage() {
        List<BookIndexDTO> full = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            full.add(book(i));
        }
        when(bookClient.pageBooks(eq(1), eq(100))).thenReturn(R.ok(pageOf(full)));
        when(bookClient.pageBooks(eq(2), eq(100)))
                .thenReturn(R.ok(pageOf(List.of(book(101), book(102)))));

        long total = service.reindex("book");

        assertThat(total).isEqualTo(102);
        verify(bookClient, times(2)).pageBooks(anyInt(), anyInt());
        verify(searchService, times(102)).index(any());
    }

    @Test
    @DisplayName("book 重建：首页为空 → 返回 0，不写任何文档")
    void reindexBookShouldReturnZeroOnEmptyPage() {
        when(bookClient.pageBooks(eq(1), eq(100))).thenReturn(R.ok(pageOf(List.of())));

        long total = service.reindex("book");

        assertThat(total).isZero();
        verify(searchService, never()).index(any());
    }

    @Test
    @DisplayName("book 重建：下游降级（R.code=40002）→ 抛 BizException 中止且不写文档")
    void reindexBookShouldAbortOnDegrade() {
        when(bookClient.pageBooks(eq(1), eq(100)))
                .thenReturn(R.fail(ResultCode.SERVICE_DEGRADED.getCode(), "服务降级"));

        assertThatThrownBy(() -> service.reindex("book"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("moyue-content");
        verify(searchService, never()).index(any());
    }

    @Test
    @DisplayName("book 重建：下游响应无数据（R.ok(null)）→ 抛 BizException 中止")
    void reindexBookShouldAbortOnNullData() {
        when(bookClient.pageBooks(eq(1), eq(100))).thenReturn(R.ok(null));

        assertThatThrownBy(() -> service.reindex("book")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("护栏：下游永远返回整页 → 拉满 10000 页即停（total=1000000），不死循环")
    void reindexBookShouldStopAtMaxPagesGuard() {
        List<BookIndexDTO> full = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            full.add(book(i));
        }
        when(bookClient.pageBooks(anyInt(), eq(100))).thenReturn(R.ok(pageOf(full)));

        long total = service.reindex("book");

        assertThat(total).isEqualTo(100L * 10000);
        verify(bookClient, times(10000)).pageBooks(anyInt(), eq(100));
    }

    // ------------------------------ chapter ------------------------------

    @Test
    @DisplayName("chapter 重建：分页拉取已发布章节 → ChapterDocument 覆盖写入")
    void reindexChapterShouldSaveDocuments() {
        when(chapterClient.pageChapters(eq(1), eq(100)))
                .thenReturn(R.ok(pageOf(List.of(chapter(1), chapter(2)))));

        long total = service.reindex("chapter");

        assertThat(total).isEqualTo(2);
        ArgumentCaptor<ChapterDocument> captor = ArgumentCaptor.forClass(ChapterDocument.class);
        verify(chapterRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getChapterId()).isEqualTo(1L);
        assertThat(captor.getAllValues().get(1).getChapterId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("chapter 重建：载荷含 null 元素 → 跳过不计数，不抛异常")
    void reindexChapterShouldSkipNullDto() {
        List<ChapterIndexDTO> records = new ArrayList<>();
        records.add(chapter(1L));
        records.add(null);
        when(chapterClient.pageChapters(eq(1), eq(100))).thenReturn(R.ok(pageOf(records)));

        long total = service.reindex("chapter");

        assertThat(total).isEqualTo(1);
        verify(chapterRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("qa 重建：分页拉取问答对 → QaDocument 映射写入（messageId/question/answer）")
    void reindexQaShouldMapAndSaveDocuments() {
        when(aiClient.pageQa(eq(1), eq(100))).thenReturn(R.ok(pageOf(List.of(qa(11), qa(12)))));

        long total = service.reindex("qa");

        assertThat(total).isEqualTo(2);
        ArgumentCaptor<QaDocument> captor = ArgumentCaptor.forClass(QaDocument.class);
        verify(qaRepository, times(2)).save(captor.capture());
        QaDocument first = captor.getAllValues().get(0);
        assertThat(first.getMessageId()).isEqualTo(11L);
        assertThat(first.getQuestion()).isEqualTo("问11");
        assertThat(first.getAnswer()).isEqualTo("答11");
    }

    @Test
    @DisplayName("qa 重建：下游降级 → 抛 BizException 含 moyue-ai，不写文档")
    void reindexQaShouldAbortOnDegrade() {
        when(aiClient.pageQa(eq(1), eq(100)))
                .thenReturn(R.fail(ResultCode.SERVICE_DEGRADED.getCode(), "服务降级"));

        assertThatThrownBy(() -> service.reindex("qa"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("moyue-ai");
        verify(qaRepository, never()).save(any());
    }

    // ------------------------------ 通用 ------------------------------

    @Test
    @DisplayName("非法类型 → PARAM_ERROR BizException，不触发任何下游调用")
    void reindexShouldRejectInvalidType() {
        assertThatThrownBy(() -> service.reindex("hack"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(bookClient, never()).pageBooks(anyInt(), anyInt());
        verify(chapterClient, never()).pageChapters(anyInt(), anyInt());
        verify(aiClient, never()).pageQa(anyInt(), anyInt());
    }

    @Test
    @DisplayName("幂等重跑：chapter 重建连跑两次 → 每次写入同量（覆盖写幂等）")
    void reindexChapterShouldBeIdempotentOnRerun() {
        when(chapterClient.pageChapters(eq(1), eq(100)))
                .thenReturn(R.ok(pageOf(List.of(chapter(1)))));

        long first = service.reindex("chapter");
        long second = service.reindex("chapter");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        verify(chapterRepository, times(2)).save(any());
    }
}

package com.moyue.author.center;

import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import com.moyue.book.service.BookService;
import com.moyue.chapter.mapper.ChapterMapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.operation.service.AuthorIncomeService;
import com.moyue.read.mapper.BookshelfMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthorCenterService 聚合编排纯单测（Mockito 隔离全部依赖，不启 Spring）。
 * 覆盖 overview / bookDashboard / listIncome 的聚合口径、收入汇总、评分加权、归属校验与降级。
 */
class AuthorCenterServiceTest {

    private final BookService bookService = mock(BookService.class);
    private final BookshelfMapper bookshelfMapper = mock(BookshelfMapper.class);
    private final ChapterMapper chapterMapper = mock(ChapterMapper.class);
    private final AuthorIncomeService authorIncomeService = mock(AuthorIncomeService.class);
    private final BookMapper bookMapper = mock(BookMapper.class);

    private AuthorCenterService service;

    @BeforeEach
    void setUp() {
        service = new AuthorCenterService();
        ReflectionTestUtils.setField(service, "bookService", bookService);
        ReflectionTestUtils.setField(service, "bookshelfMapper", bookshelfMapper);
        ReflectionTestUtils.setField(service, "chapterMapper", chapterMapper);
        ReflectionTestUtils.setField(service, "authorIncomeService", authorIncomeService);
        ReflectionTestUtils.setField(service, "bookMapper", bookMapper);
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private BookSummaryDTO bookDto(Long bookId, long click) {
        BookSummaryDTO d = new BookSummaryDTO();
        d.setBookId(bookId);
        d.setClickCount(click);
        d.setAuthorId(1L);
        d.setTitle("书" + bookId);
        d.setCategory("玄幻");
        d.setStatus(1);
        d.setWordCount(1000);
        return d;
    }

    private PageResult<BookSummaryDTO> pageOf(List<BookSummaryDTO> records, long total) {
        PageResult<BookSummaryDTO> p = new PageResult<>();
        p.setRecords(records);
        p.setTotal(total);
        p.setPage(1);
        p.setSize(records.size());
        return p;
    }

    private AuthorIncomeDTO income(Long id, Long bookId, BigDecimal amount, String month) {
        AuthorIncomeDTO d = new AuthorIncomeDTO();
        d.setId(id);
        d.setAuthorId(1L);
        d.setBookId(bookId);
        d.setIncomeType(1);
        d.setAmount(amount);
        d.setSettleMonth(month);
        d.setCreateTime(LocalDateTime.now());
        return d;
    }

    private String currentMonth() {
        return java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String prevMonth() {
        return java.time.YearMonth.now().minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    // ------------------------------ overview 聚合 ------------------------------

    @Test
    @DisplayName("authorOverview 跨四源聚合：totalBooks/click/favorite/chapterStat/income/rating 正确")
    void authorOverview_aggregatesAllSources() {
        when(bookService.listMyBooks(1L, 1, 10000)).thenReturn(pageOf(
                Arrays.asList(bookDto(1L, 100L), bookDto(2L, 200L)), 2L));
        when(bookshelfMapper.countByBookIds(anyList())).thenReturn(5);
        when(chapterMapper.countByBookIdsAndStatus(anyList(), eq(0))).thenReturn(3);
        when(chapterMapper.countByBookIdsAndStatus(anyList(), eq(2))).thenReturn(4);
        when(chapterMapper.countByBookIdsAndStatus(anyList(), eq(3))).thenReturn(1);
        when(chapterMapper.countByBookIdsAndStatus(anyList(), eq(4))).thenReturn(2);
        when(authorIncomeService.listByAuthor(1L)).thenReturn(Arrays.asList(
                income(1L, 1L, new BigDecimal("100.00"), currentMonth()),
                income(2L, 1L, new BigDecimal("50.00"), currentMonth()),
                income(3L, 1L, new BigDecimal("30.00"), prevMonth())));
        BookEntity b1 = new BookEntity();
        b1.setRatingAvg(new BigDecimal("4.00"));
        b1.setRatingCount(10);
        BookEntity b2 = new BookEntity();
        b2.setRatingAvg(new BigDecimal("5.00"));
        b2.setRatingCount(20);
        when(bookMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(b1, b2));

        AuthorDashboardVO vo = service.authorOverview(1L);

        assertThat(vo.getTotalBooks()).isEqualTo(2);
        assertThat(vo.getTotalClick()).isEqualTo(300L);
        assertThat(vo.getTotalFavorite()).isEqualTo(5L);
        assertThat(vo.getChapterStats().getDraft()).isEqualTo(3L);
        assertThat(vo.getChapterStats().getPublished()).isEqualTo(4L);
        assertThat(vo.getChapterStats().getRejected()).isEqualTo(1L);
        assertThat(vo.getChapterStats().getScheduled()).isEqualTo(2L);
        assertThat(vo.getTotalIncome()).isEqualByComparingTo("180.00");
        assertThat(vo.getMonthIncome()).isEqualByComparingTo("150.00");
        // 加权: (4*10 + 5*20)/30 = 140/30 = 4.67
        assertThat(vo.getRatingAvg()).isEqualByComparingTo("4.67");
        assertThat(vo.getRatingCount()).isEqualTo(30);
    }

    @Test
    @DisplayName("authorOverview 无作品：聚合全为 0，rating 0.00")
    void authorOverview_emptyBooks() {
        when(bookService.listMyBooks(1L, 1, 10000)).thenReturn(pageOf(List.of(), 0L));
        when(authorIncomeService.listByAuthor(1L)).thenReturn(List.of());

        AuthorDashboardVO vo = service.authorOverview(1L);

        assertThat(vo.getTotalBooks()).isZero();
        assertThat(vo.getTotalClick()).isZero();
        assertThat(vo.getTotalFavorite()).isZero();
        assertThat(vo.getChapterStats().getDraft()).isZero();
        assertThat(vo.getTotalIncome()).isEqualByComparingTo("0.00");
        assertThat(vo.getMonthIncome()).isEqualByComparingTo("0.00");
        assertThat(vo.getRatingAvg()).isEqualByComparingTo("0.00");
        assertThat(vo.getRatingCount()).isZero();
    }

    @Test
    @DisplayName("authorOverview authorIncomeService 为 null：降级空，不抛 NPE")
    void authorOverview_authorIncomeNull_safe() {
        ReflectionTestUtils.setField(service, "authorIncomeService", null);
        when(bookService.listMyBooks(1L, 1, 10000)).thenReturn(pageOf(List.of(bookDto(1L, 10L)), 1L));

        AuthorDashboardVO vo = service.authorOverview(1L);
        assertThat(vo.getTotalIncome()).isEqualByComparingTo("0.00");
    }

    // ------------------------------ bookDashboard 归属 ------------------------------

    @Test
    @DisplayName("bookDashboard 作者本人：单书聚合正确")
    void bookDashboard_owner_success() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(1L, 2));
        BookSummaryDTO b = bookDto(10L, 50L);
        b.setAuthorId(1L);
        when(bookService.detail(10L)).thenReturn(b);
        when(bookshelfMapper.countByBookId(10L)).thenReturn(3);
        when(chapterMapper.countByBookIdsAndStatus(anyList(), eq(2))).thenReturn(7);
        BookEntity entity = new BookEntity();
        entity.setRatingAvg(new BigDecimal("4.50"));
        entity.setRatingCount(8);
        when(bookMapper.selectById(10L)).thenReturn(entity);

        AuthorBookDashboardVO vo = service.bookDashboard(1L, 10L);

        assertThat(vo.getTotalBooks()).isEqualTo(1);
        assertThat(vo.getTotalClick()).isEqualTo(50L);
        assertThat(vo.getTotalFavorite()).isEqualTo(3L);
        assertThat(vo.getBook().getBookId()).isEqualTo(10L);
        assertThat(vo.getBook().getTitle()).isEqualTo("书10");
        assertThat(vo.getBook().getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(vo.getBook().getRatingCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("bookDashboard 非作者且非管理员：FORBIDDEN")
    void bookDashboard_notOwner_forbidden() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(1L, 2));
        BookSummaryDTO b = bookDto(10L, 50L);
        b.setAuthorId(99L);
        when(bookService.detail(10L)).thenReturn(b);

        assertThatThrownBy(() -> service.bookDashboard(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("bookDashboard 管理员(role=3) 可看他人作品")
    void bookDashboard_admin_allowed() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(1L, 3));
        BookSummaryDTO b = bookDto(10L, 50L);
        b.setAuthorId(99L);
        when(bookService.detail(10L)).thenReturn(b);
        BookEntity entity = new BookEntity();
        entity.setRatingAvg(new BigDecimal("5.00"));
        entity.setRatingCount(2);
        when(bookMapper.selectById(10L)).thenReturn(entity);

        AuthorBookDashboardVO vo = service.bookDashboard(1L, 10L);
        assertThat(vo.getBook().getBookId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("bookDashboard 书不存在：RESOURCE_NOT_FOUND")
    void bookDashboard_notFound() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(1L, 2));
        when(bookService.detail(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.bookDashboard(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("bookDashboard bookId 为 null：PARAM_ERROR")
    void bookDashboard_nullBookId() {
        assertThatThrownBy(() -> service.bookDashboard(1L, null))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("listIncome 按 bookId 过滤")
    void listIncome_filtersByBook() {
        when(authorIncomeService.listByAuthor(1L)).thenReturn(Arrays.asList(
                income(1L, 10L, new BigDecimal("10.00"), "2026-10"),
                income(2L, 20L, new BigDecimal("20.00"), "2026-10")));
        List<AuthorIncomeVO> all = service.listIncome(1L, null);
        List<AuthorIncomeVO> filtered = service.listIncome(1L, 10L);
        assertThat(all).hasSize(2);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getBookId()).isEqualTo(10L);
    }
}

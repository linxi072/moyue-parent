package com.moyue.author.center;

import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.LocalCacheTestConfig;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.operation.service.AuthorIncomeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 作者创作中心全流程集成测试（@SpringBootTest @ActiveProfiles("test")，H2 真库）。
 * 经 JdbcTemplate 清种 book / chapter / bookshelf，@MockBean 隔离 AuthorIncomeService 与 RiskClient，
 * 直接调用 AuthorCenterController 验证 overview / dashboard / income 聚合、鉴权、批量失败回滚。
 * 鉴权经 {@link SecurityContextHolder#set} 注入当前用户（与 HeaderInterceptor 运行期行为一致）。
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(LocalCacheTestConfig.class)
class AuthorDashboardIntegrationTest {

    private static final long TEST_USER = 7001L;
    private static final long TEST_BOOK = 9101L;
    private static final long OTHER_BOOK = 9102L;
    private static final long OTHER_CHAPTER = 9201L;

    @Autowired
    private AuthorCenterController controller;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AuthorIncomeService authorIncomeService;
    @MockBean
    private RiskClient riskClient;

    @BeforeEach
    void seed() {
        // 隔离真实机审：恒返回 PASS
        ModerationResultDTO pass = new ModerationResultDTO();
        pass.setDecision("PASS");
        when(riskClient.moderate(any())).thenReturn(R.ok(pass));

        // 隔离稿酬（按 bookId 过滤验证）
        when(authorIncomeService.listByAuthor(TEST_USER)).thenReturn(Arrays.asList(
                income(1L, TEST_BOOK, new BigDecimal("10.00")),
                income(2L, 9999L, new BigDecimal("20.00"))));

        jdbcTemplate.update("DELETE FROM chapter WHERE book_id IN (?,?)", TEST_BOOK, OTHER_BOOK);
        jdbcTemplate.update("DELETE FROM bookshelf WHERE book_id IN (?,?)", TEST_BOOK, OTHER_BOOK);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?,?)", TEST_BOOK, OTHER_BOOK);

        jdbcTemplate.update("INSERT INTO book (id,author_id,title,category_id,status,word_count,click_count,rating_avg,rating_count,is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)",
                TEST_BOOK, TEST_USER, "集成测试书", 1, 1, 12000, 500L, new BigDecimal("4.50"), 6, 0);
        jdbcTemplate.update("INSERT INTO book (id,author_id,title,category_id,status,word_count,click_count,rating_avg,rating_count,is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?)",
                OTHER_BOOK, 8002L, "他人书", 1, 1, 100, 10L, new BigDecimal("3.00"), 1, 0);

        // TEST_BOOK 章节：2 草稿 / 1 已发布 / 1 已驳回 / 1 定时待发布
        insertChapter(91011L, TEST_BOOK, 1, 0);
        insertChapter(91012L, TEST_BOOK, 2, 0);
        insertChapter(91013L, TEST_BOOK, 3, 2);
        insertChapter(91014L, TEST_BOOK, 4, 3);
        insertChapter(91015L, TEST_BOOK, 5, 4);
        // OTHER_BOOK 一章（草稿），用于批量发布归属失败
        insertChapter(OTHER_CHAPTER, OTHER_BOOK, 1, 0);

        // TEST_BOOK 收藏：3 有效 + 1 已删
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (1,50001,?,0)", TEST_BOOK);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (2,50002,?,0)", TEST_BOOK);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (3,50003,?,0)", TEST_BOOK);
        jdbcTemplate.update("INSERT INTO bookshelf (id,user_id,book_id,is_deleted) VALUES (4,50004,?,1)", TEST_BOOK);

        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(TEST_USER, 2));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clear();
        // 自清理：H2 内存库跨测试类复用（DB_CLOSE_DELAY=-1），本类 seed 的固定 id 若不清理会污染后续测试
        // （如 ChapterPaywallTest 同用 chapter id 9201），导致主键冲突。
        jdbcTemplate.update("DELETE FROM chapter WHERE book_id IN (?,?)", TEST_BOOK, OTHER_BOOK);
        jdbcTemplate.update("DELETE FROM bookshelf WHERE book_id IN (?,?)", TEST_BOOK, OTHER_BOOK);
        jdbcTemplate.update("DELETE FROM book WHERE id IN (?,?)", TEST_BOOK, OTHER_BOOK);
    }

    @Test
    @DisplayName("overview 聚合与 H2 数据一致")
    void overview_matchesH2() {
        R<AuthorDashboardVO> r = controller.overview();
        assertThat(r.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        AuthorDashboardVO vo = r.getData();
        assertThat(vo.getTotalBooks()).isEqualTo(1);
        assertThat(vo.getTotalClick()).isEqualTo(500L);
        assertThat(vo.getTotalFavorite()).isEqualTo(3L);
        assertThat(vo.getChapterStats().getDraft()).isEqualTo(2L);
        assertThat(vo.getChapterStats().getPublished()).isEqualTo(1L);
        assertThat(vo.getChapterStats().getRejected()).isEqualTo(1L);
        assertThat(vo.getChapterStats().getScheduled()).isEqualTo(1L);
        assertThat(vo.getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(vo.getRatingCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("bookDashboard 单书聚合与 H2 一致")
    void bookDashboard_matchesH2() {
        R<AuthorBookDashboardVO> r = controller.bookDashboard(TEST_BOOK);
        assertThat(r.getCode()).isEqualTo(ResultCode.SUCCESS.getCode());
        AuthorBookDashboardVO vo = r.getData();
        assertThat(vo.getTotalBooks()).isEqualTo(1);
        assertThat(vo.getTotalClick()).isEqualTo(500L);
        assertThat(vo.getTotalFavorite()).isEqualTo(3L);
        assertThat(vo.getBook().getBookId()).isEqualTo(TEST_BOOK);
        assertThat(vo.getBook().getRatingAvg()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("listIncome 按 bookId 过滤")
    void income_filtersByBook() {
        R<List<AuthorIncomeVO>> r = controller.listIncome(TEST_BOOK);
        assertThat(r.getData()).hasSize(1);
        assertThat(r.getData().get(0).getBookId()).isEqualTo(TEST_BOOK);
    }

    @Test
    @DisplayName("匿名访问 overview → UNAUTHORIZED(10002)")
    void anonymous_forbidden() {
        SecurityContextHolder.clear();
        assertThatThrownBy(() -> controller.overview())
                .isInstanceOf(com.moyue.common.BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("读者(role=1) 访问 overview → FORBIDDEN(10003)")
    void reader_forbidden() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(TEST_USER, 1));
        assertThatThrownBy(() -> controller.overview())
                .isInstanceOf(com.moyue.common.BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("batchUpdateStatus targetStatus=2 → PARAM_ERROR(10001)")
    void batchStatus_invalidTarget() {
        AuthorCenterController.BatchStatusRequest req = new AuthorCenterController.BatchStatusRequest();
        req.setChapterIds(Arrays.asList(91011L));
        req.setTargetStatus(2);
        assertThatThrownBy(() -> controller.batchStatus(req))
                .isInstanceOf(com.moyue.common.BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("批量发布他人章节 → 整批失败回滚，返回 R(20002, failedIds)")
    void batchPublish_notOwned_rollsBack() {
        AuthorCenterController.BatchPublishRequest req = new AuthorCenterController.BatchPublishRequest();
        req.setChapterIds(Arrays.asList(OTHER_CHAPTER));
        R<BatchResult> r = controller.batchPublish(req);
        assertThat(r.getCode()).isEqualTo(ResultCode.CONTENT_BLOCKED.getCode());
        BatchResult br = r.getData();
        assertThat(br.isSuccess()).isFalse();
        assertThat(br.getFailedIds()).containsExactly(OTHER_CHAPTER);
    }

    private void insertChapter(Long id, Long bookId, int chapterNo, int status) {
        jdbcTemplate.update("INSERT INTO chapter (id,book_id,chapter_no,title,content,word_count,status,is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?,?)",
                id, bookId, chapterNo, "章" + id, "正文" + id, 100, status, 0);
    }

    private AuthorIncomeDTO income(Long id, Long bookId, BigDecimal amount) {
        AuthorIncomeDTO d = new AuthorIncomeDTO();
        d.setId(id);
        d.setAuthorId(TEST_USER);
        d.setBookId(bookId);
        d.setIncomeType(1);
        d.setAmount(amount);
        d.setSettleMonth(java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        return d;
    }
}

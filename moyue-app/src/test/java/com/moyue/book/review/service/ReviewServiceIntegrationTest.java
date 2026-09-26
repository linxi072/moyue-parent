package com.moyue.book.review.service;

import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.book.entity.BookEntity;
import com.moyue.book.mapper.BookMapper;
import com.moyue.book.review.entity.ReviewEntity;
import com.moyue.book.review.mapper.ReviewMapper;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 书评与评分集成测试（@SpringBootTest @ActiveProfiles("test")，H2 真库）。
 * 用 {@link MockBean} 隔离真实机审（RiskClient 恒返回 PASS），其余走真实 Bean（ReviewMapper /
 * BookService / ReviewService 自动装配），验证「提交→审核通过→聚合回写 book 评分」的真实链路。
 *
 * <p>设计说明：本模块书评默认 {@code status=0}（待审），聚合与列表仅统计 {@code status=1}（已通过）。
 * 因此「评分联动 book」必须经由正常流程：addReview（落库待审）→ auditReview(id, 1)（置为已通过）→
 * 重算聚合回写 book.rating_avg / rating_count。此测试覆盖的是这条真实链路，而非假设提交即生效。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewServiceIntegrationTest {

    private static final Long BOOK_ID = 9001L;
    private static final Long USER_ID = 7L;

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private RiskClient riskClient;

    @BeforeEach
    void cleanAndSeed() {
        // 隔离真实机审：恒返回 PASS，避免测试依赖外部 moyue-risk
        ModerationResultDTO pass = new ModerationResultDTO();
        pass.setDecision("PASS");
        when(riskClient.moderate(any())).thenReturn(R.ok(pass));

        // 物理清空书评表 + 当前测试专用作品，保证测试间相互隔离（H2 内存库跨测试共享）
        jdbcTemplate.execute("DELETE FROM book_review");
        jdbcTemplate.execute("DELETE FROM book WHERE id = " + BOOK_ID);

        BookEntity book = new BookEntity();
        book.setId(BOOK_ID);
        book.setAuthorId(1L);
        book.setTitle("书评集成测试专用书");
        book.setCategoryId(1L);
        book.setStatus(1);
        book.setRatingAvg(BigDecimal.ZERO);
        book.setRatingCount(0);
        book.setClickCount(0L);
        book.setIsDeleted(0);
        bookMapper.insert(book);
    }

    // ------------------------------ 聚合回写 book 评分 ------------------------------

    @Test
    @DisplayName("提交并审核通过两条书评（4 星 / 5 星）→ book 评分聚合为 4.50 / 2")
    void addReview_approved_rewritesBookRating() {
        ReviewEntity r1 = reviewService.addReview(USER_ID, BOOK_ID, 4, "不错");
        reviewService.auditReview(r1.getId(), 1);
        ReviewEntity r2 = reviewService.addReview(USER_ID, BOOK_ID, 5, "很好");
        reviewService.auditReview(r2.getId(), 1);

        BookEntity book = bookMapper.selectById(BOOK_ID);
        assertThat(book.getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(book.getRatingCount()).isEqualTo(2);
    }

    // ------------------------------ 列表过滤未通过 ------------------------------

    @Test
    @DisplayName("listByBook 仅返回 status=1：直接插入的 status=2 书评被过滤")
    void listByBook_filtersUnapproved() {
        // 直接插入一条 status=2（绕过机审），不应出现在列表
        ReviewEntity rejected = new ReviewEntity();
        rejected.setId(8002L);
        rejected.setUserId(99L);
        rejected.setBookId(BOOK_ID);
        rejected.setScore(3);
        rejected.setContent("被驳回的书评");
        rejected.setStatus(2);
        rejected.setLikeCount(0);
        rejected.setIsDeleted(0);
        reviewMapper.insert(rejected);

        // 经正常流程提交并审核通过一条 status=1
        ReviewEntity approved = reviewService.addReview(USER_ID, BOOK_ID, 5, "好评");
        reviewService.auditReview(approved.getId(), 1);

        PageResult<ReviewEntity> res = reviewService.listByBook(BOOK_ID, 1, 10);

        assertThat(res.getRecords()).extracting(ReviewEntity::getStatus).containsOnly(1);
        assertThat(res.getRecords()).noneMatch(r -> r.getId().equals(rejected.getId()));
        assertThat(res.getRecords()).anyMatch(r -> r.getId().equals(approved.getId()));
    }

    // ------------------------------ 删除后重算聚合 ------------------------------

    @Test
    @DisplayName("删除一条已通过书评 → book 评分聚合相应变化（(4+5)/2=4.50/2 → 删 4 星 → 5.00/1）")
    void deleteReview_recalculatesAggregation() {
        ReviewEntity r1 = reviewService.addReview(USER_ID, BOOK_ID, 4, "不错");
        reviewService.auditReview(r1.getId(), 1);
        ReviewEntity r2 = reviewService.addReview(USER_ID, BOOK_ID, 5, "很好");
        reviewService.auditReview(r2.getId(), 1);

        BookEntity before = bookMapper.selectById(BOOK_ID);
        assertThat(before.getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(before.getRatingCount()).isEqualTo(2);

        // 管理员删除（role=3 为管理员，可删他人/任意书评）
        reviewService.deleteReview(r1.getId(), USER_ID, 3);

        BookEntity after = bookMapper.selectById(BOOK_ID);
        assertThat(after.getRatingAvg()).isEqualByComparingTo("5.00");
        assertThat(after.getRatingCount()).isEqualTo(1);
    }
}

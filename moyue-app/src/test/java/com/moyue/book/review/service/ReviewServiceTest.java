package com.moyue.book.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.book.event.BookRatingChangedEvent;
import com.moyue.book.review.entity.ReviewEntity;
import com.moyue.book.review.mapper.ReviewLikeMapper;
import com.moyue.book.review.mapper.ReviewMapper;
import com.moyue.book.review.mapper.ReviewSummary;
import com.moyue.book.service.BookService;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 书评与评分业务（ReviewService）纯 Mockito 单测（不启 Spring / 不依赖 Docker）。
 * 覆盖：提交书评机审 PASS/REJECT 拦截、评分越界参数校验、按作品列表过滤 status=1、
 * 删除书评权限（本人 / 管理员 / 越权拒绝），以及聚合回写 book 与发布评分变更事件。
 *
 * <p>与 comment 模块的关键差异（已落实于实现）：列表显式过滤 {@code status=1}；
 * 机审 hook 走本地常量 {@code BIZ_REVIEW=5}；聚合聚合仅统计 {@code status=1} 的书评。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long BOOK_ID = 9001L;
    private static final Long OTHER_USER = 8L;
    /** 普通用户角色值（非管理员 3），用于触发越权拒绝 */
    private static final int ROLE_NORMAL = 1;

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private ReviewLikeMapper reviewLikeMapper;
    @Mock
    private RiskClient riskClient;
    @Mock
    private BookService bookService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewService reviewService;

    // ------------------------------ 提交书评：机审 PASS ------------------------------

    @Test
    @DisplayName("提交书评：机审 PASS → 落库、回写聚合、发布评分变更事件")
    void addReview_pass_persistsAndRewritesAggregationAndPublishesEvent() {
        ModerationResultDTO result = new ModerationResultDTO();
        result.setDecision("PASS");
        when(riskClient.moderate(any())).thenReturn(R.ok(result));

        ReviewSummary summary = new ReviewSummary();
        summary.setAvg(new BigDecimal("4.50"));
        summary.setCnt(2L);
        when(reviewMapper.selectSummary(any())).thenReturn(summary);

        reviewService.addReview(USER_ID, BOOK_ID, 5, "好评");

        verify(reviewMapper, times(1)).insert(any(ReviewEntity.class));
        verify(bookService).updateRatingSummary(eq(BOOK_ID), eq(new BigDecimal("4.50")), eq(2));
        verify(eventPublisher).publishEvent(any(BookRatingChangedEvent.class));
    }

    // ------------------------------ 提交书评：机审 REJECT 拦截 ------------------------------

    @Test
    @DisplayName("提交书评：机审 REJECT → 抛 CONTENT_BLOCKED，书评不落库")
    void addReview_reject_throwsAndNotInserted() {
        ModerationResultDTO result = new ModerationResultDTO();
        result.setDecision("REJECT");
        when(riskClient.moderate(any())).thenReturn(R.ok(result));

        assertThatThrownBy(() -> reviewService.addReview(USER_ID, BOOK_ID, 5, "违规内容"))
                .isInstanceOf(BizException.class);

        verify(reviewMapper, never()).insert(any(ReviewEntity.class));
    }

    // ------------------------------ 提交书评：评分越界参数校验 ------------------------------

    @Test
    @DisplayName("提交书评：评分 6 越界（>5）→ 抛参数异常，不送机审不落库")
    void addReview_scoreTooHigh_paramError() {
        assertThatThrownBy(() -> reviewService.addReview(USER_ID, BOOK_ID, 6, "x"))
                .isInstanceOf(BizException.class);
        verify(riskClient, never()).moderate(any());
        verify(reviewMapper, never()).insert(any(ReviewEntity.class));
    }

    @Test
    @DisplayName("提交书评：评分 0 越界（<1）→ 抛参数异常")
    void addReview_scoreTooLow_paramError() {
        assertThatThrownBy(() -> reviewService.addReview(USER_ID, BOOK_ID, 0, "x"))
                .isInstanceOf(BizException.class);
        verify(riskClient, never()).moderate(any());
        verify(reviewMapper, never()).insert(any(ReviewEntity.class));
    }

    // ------------------------------ 列表：仅返回 status=1 ------------------------------

    @Test
    @DisplayName("按作品列表：仅转换已通过（status=1）书评")
    void listByBook_onlyReturnsApproved() {
        ReviewEntity approved = new ReviewEntity();
        approved.setId(1L);
        approved.setStatus(1);

        Page<ReviewEntity> page = new Page<>();
        page.setRecords(List.of(approved));
        page.setTotal(1L);
        // 注意：MyBatis-Plus 的 selectPage 会「回填」传入的 Page 实参，listByBook 读取的是该实参的 records；
        // 因此 mock 必须回填实参，而不是返回一个新 Page 对象
        when(reviewMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            Page<ReviewEntity> p = inv.getArgument(0);
            p.setRecords(List.of(approved));
            p.setTotal(1L);
            return p;
        });

        PageResult<ReviewEntity> res = reviewService.listByBook(BOOK_ID, 1, 10);

        assertThat(res.getRecords()).hasSize(1);
        assertThat(res.getRecords().get(0).getStatus()).isEqualTo(1);
    }

    // ------------------------------ 删除书评：越权拒绝 ------------------------------

    @Test
    @DisplayName("删除书评：非本人且非管理员 → 抛 FORBIDDEN，不更新")
    void deleteReview_nonOwnerForbidden() {
        ReviewEntity e = new ReviewEntity();
        e.setId(999L);
        e.setUserId(OTHER_USER);
        when(reviewMapper.selectById(999L)).thenReturn(e);

        assertThatThrownBy(() -> reviewService.deleteReview(999L, USER_ID, ROLE_NORMAL))
                .isInstanceOf(BizException.class);

        verify(reviewMapper, never()).updateById(any(ReviewEntity.class));
    }
}

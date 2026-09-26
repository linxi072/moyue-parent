package com.moyue.api.book.client;

import com.moyue.book.review.dto.ReviewDTO;
import com.moyue.book.review.service.ReviewService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.springframework.stereotype.Component;

/**
 * 书评服务进程内适配器（monolith 版），镜像 CommentClient。
 */
@Component
public class ReviewClient {

    private final ReviewService reviewService;

    public ReviewClient(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 审核回写书评状态（1=已通过 / 2=已驳回） */
    public R<Void> auditReview(Long reviewId, Integer status) {
        try {
            reviewService.auditReview(reviewId, status);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 按 ID 查询单条书评（内部端点用，书评不存在返回 data=null） */
    public R<ReviewDTO> getReview(Long reviewId) {
        try {
            return R.ok(ReviewDTO.toDto(reviewService.getById(reviewId)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

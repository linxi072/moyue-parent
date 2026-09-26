package com.moyue.book.review.controller;

import com.moyue.book.review.dto.ReviewDTO;
import com.moyue.book.review.entity.ReviewEntity;
import com.moyue.book.review.service.ReviewService;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书评内部端点（仅服务间调用，由网关注入 X-Service-Token 鉴权）：审核回写、按 ID 查询。
 * 与 BookService / AuditService 内部端点同前缀 /api/v1/internal。
 */
@RestController
@RequestMapping("/api/v1/internal")
public class ReviewInternalController {

    @Autowired
    private ReviewService reviewService;

    /** 审核回写：PUT /api/v1/internal/reviews/{reviewId}/audit?status= */
    @PutMapping("/reviews/{reviewId}/audit")
    public R<Void> audit(@PathVariable Long reviewId, @RequestParam Integer status) {
        reviewService.auditReview(reviewId, status);
        return R.ok();
    }

    /** 按 ID 查询书评（供审核系统 / 内部调用）：GET /api/v1/internal/reviews/{reviewId} */
    @GetMapping("/reviews/{reviewId}")
    public R<ReviewDTO> get(@PathVariable Long reviewId) {
        ReviewEntity e = reviewService.getById(reviewId);
        return R.ok(ReviewDTO.toDto(e));
    }
}

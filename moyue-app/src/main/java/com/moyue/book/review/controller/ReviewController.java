package com.moyue.book.review.controller;

import com.moyue.book.review.dto.ReviewDTO;
import com.moyue.book.review.entity.ReviewEntity;
import com.moyue.book.review.service.ReviewService;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书评接口：查询 / 发表 / 删除 / 点赞。
 * 路径前缀 /api/v1 与网关路由保持一致。
 * 评论人一律取网关注入的 X-User-Id，不接受请求体传入 userId（防越权）。
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    /** 按书籍分页查询书评（仅 status=1）：GET /api/v1/reviews?bookId=&page=&size= */
    @GetMapping("/reviews")
    public R<PageResult<ReviewDTO>> list(@RequestParam Long bookId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return R.ok(ReviewDTO.toDtoPage(reviewService.listByBook(bookId, page, size)));
    }

    /** 发表书评：POST /api/v1/reviews（评论人取网关注入头） */
    @PostMapping("/reviews")
    public R<ReviewDTO> add(@RequestBody CreateReviewRequest req, HttpServletRequest request) {
        long userId = requireUserId(request);
        ReviewEntity e = reviewService.addReview(userId, req.getBookId(), req.getScore(), req.getContent());
        return R.ok(ReviewDTO.toDto(e));
    }

    /** 删除书评（本人 / 管理员） */
    @DeleteMapping("/reviews/{reviewId}")
    public R<Void> delete(@PathVariable Long reviewId, HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        reviewService.deleteReview(reviewId, userId, role);
        return R.ok();
    }

    /** 点赞切换：POST /api/v1/reviews/{reviewId}/like（返回当前 like_count） */
    @PostMapping("/reviews/{reviewId}/like")
    public R<Integer> toggleLike(@PathVariable Long reviewId, HttpServletRequest request) {
        long userId = requireUserId(request);
        return R.ok(reviewService.toggleLike(reviewId, userId));
    }

    // ------------------------------ 上下文工具 ------------------------------

    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    private int currentRole(HttpServletRequest request) {
        String role = request.getHeader(Constants.USER_ROLE_HEADER);
        if (role == null || role.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(role.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------ 请求体 ------------------------------

    /** 发表书评请求 */
    @Data
    public static class CreateReviewRequest {
        private Long bookId;
        private Integer score;
        private String content;
    }
}

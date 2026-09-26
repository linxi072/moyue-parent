package com.moyue.book.review.dto;

import com.moyue.book.review.entity.ReviewEntity;
import com.moyue.common.core.domain.PageResult;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ReviewDTO implements Serializable {

    private Long reviewId;
    private Long userId;
    private Long bookId;
    private Integer score;
    private String content;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createTime;

    public static ReviewDTO toDto(ReviewEntity e) {
        if (e == null) {
            return null;
        }
        ReviewDTO d = new ReviewDTO();
        d.setReviewId(e.getId());
        d.setUserId(e.getUserId());
        d.setBookId(e.getBookId());
        d.setScore(e.getScore());
        d.setContent(e.getContent());
        d.setLikeCount(e.getLikeCount());
        d.setStatus(e.getStatus());
        d.setCreateTime(e.getCreateTime());
        return d;
    }

    public static PageResult<ReviewDTO> toDtoPage(PageResult<ReviewEntity> page) {
        PageResult<ReviewDTO> r = new PageResult<>();
        r.setTotal(page.getTotal());
        r.setPage(page.getPage());
        r.setSize(page.getSize());
        r.setRecords(page.getRecords().stream().map(ReviewDTO::toDto).toList());
        return r;
    }
}

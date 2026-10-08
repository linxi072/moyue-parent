package com.moyue.social.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论视图。
 *
 * @author moyue
 */
@Data
@Builder
public class CommentVO {

    private Long id;
    private Long bookId;
    private Long chapterId;
    private Long userId;
    private Long replyTo;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
}

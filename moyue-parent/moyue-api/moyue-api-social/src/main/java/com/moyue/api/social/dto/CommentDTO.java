package com.moyue.api.social.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 评论 DTO（跨服务共享）。
 */
@Data
public class CommentDTO implements Serializable {

    private Long id;

    private Long userId;

    private Long bookId;

    private String content;

    /** 0 待审 / 1 已通过 / 2 已驳回 */
    private Integer status;

    private Integer likeCount;

    private LocalDateTime createTime;
}

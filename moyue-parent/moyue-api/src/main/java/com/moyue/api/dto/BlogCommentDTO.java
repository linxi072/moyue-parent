package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 博客评论数据传输对象（跨服务共享）。
 */
@Data
public class BlogCommentDTO implements Serializable {

    private Long id;

    private Long postId;

    private Long userId;

    private String userName;

    private String content;

    private Integer likeCount;

    private LocalDateTime createTime;
}

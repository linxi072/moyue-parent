package com.moyue.api.social.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 博客文章数据传输对象（跨服务共享）。
 */
@Data
public class BlogPostDTO implements Serializable {

    private Long id;

    private Long authorId;

    private String authorName;

    private String title;

    private String coverUrl;

    private String summary;

    private String content;

    /** 0 草稿 / 1 已发布 / 2 已下架 */
    private Integer status;

    private Integer likeCount;

    private Integer commentCount;

    private Integer viewCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

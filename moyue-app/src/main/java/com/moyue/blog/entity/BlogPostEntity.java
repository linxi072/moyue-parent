package com.moyue.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客文章实体，映射 blog_post 表。
 */
@Data
@TableName("blog_post")
public class BlogPostEntity {

    /** 文章主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作者 → user.id */
    private Long authorId;

    /** 标题 */
    private String title;

    /** 封面 */
    private String coverUrl;

    /** 摘要 */
    private String summary;

    /** 正文 */
    private String content;

    /** 状态：0 草稿 / 1 已发布 / 2 已下架 */
    private Integer status;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 浏览数 */
    private Integer viewCount;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

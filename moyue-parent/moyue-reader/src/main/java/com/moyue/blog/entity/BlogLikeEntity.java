package com.moyue.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客点赞实体，映射 blog_like 表。
 * 唯一键 uk_post_user(post_id, user_id) 防重复点赞；取消点赞采用逻辑删除。
 */
@Data
@TableName("blog_like")
public class BlogLikeEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 文章 ID → blog_post.id */
    private Long postId;

    /** 点赞人 → user.id */
    private Long userId;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

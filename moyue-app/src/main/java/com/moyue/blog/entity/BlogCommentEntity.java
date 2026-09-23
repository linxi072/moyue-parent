package com.moyue.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 博客评论实体，映射 blog_comment 表。
 */
@Data
@TableName("blog_comment")
public class BlogCommentEntity {

    /** 评论主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 文章 ID → blog_post.id */
    private Long postId;

    /** 评论人 → user.id */
    private Long userId;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

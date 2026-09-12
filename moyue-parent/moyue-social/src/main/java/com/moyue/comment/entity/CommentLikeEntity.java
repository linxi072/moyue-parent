package com.moyue.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论点赞实体，映射 comment_like 表（防重复点赞）。
 * 取消点赞走全局逻辑删除（is_deleted=1），再次点赞时需复活旧记录。
 */
@Data
@TableName("comment_like")
public class CommentLikeEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 评论 ID → comment.id */
    private Long commentId;

    /** 点赞人 ID → user.id */
    private Long userId;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

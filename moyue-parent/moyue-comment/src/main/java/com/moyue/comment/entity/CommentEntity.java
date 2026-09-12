package com.moyue.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评论实体，映射 comment 表。
 * comment 表含 is_deleted 字段，全局逻辑删除配置（logic-delete-field: isDeleted）会自动生效。
 */
@Data
@TableName("comment")
public class CommentEntity {

    /** 评论主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 评论人 ID → user.id */
    private Long userId;

    /** 作品 ID → book.id */
    private Long bookId;

    /** 章节 ID，书评为 NULL */
    private Long chapterId;

    /** 评论内容 */
    private String content;

    /** 机审风险分 0.000-1.000 */
    private BigDecimal auditScore;

    /** 状态：0 待审 / 1 已通过 / 2 已驳回 */
    private Integer status;

    /** 点赞数 */
    private Integer likeCount;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;
}

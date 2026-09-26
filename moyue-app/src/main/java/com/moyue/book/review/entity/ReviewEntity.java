package com.moyue.book.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("book_review")
public class ReviewEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 评论人 → user.id */
    private Long userId;

    /** 作品 ID → book.id */
    private Long bookId;

    /** 星级 1~5 */
    private Integer score;

    /** 书评内容 */
    private String content;

    /** 机审风险分 0.000~1.000（内部风控，DTO 不外露） */
    private BigDecimal auditScore;

    /** 0 待审 / 1 已通过 / 2 已驳回 */
    private Integer status;

    /** 点赞数 */
    private Integer likeCount;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

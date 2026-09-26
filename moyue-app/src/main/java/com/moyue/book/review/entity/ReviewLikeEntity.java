package com.moyue.book.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_like")
public class ReviewLikeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long reviewId;

    private Long userId;

    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

package com.moyue.social.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 评论点赞记录（唯一键防重）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_comment_like")
public class CommentLike extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long commentId;
    private Long userId;
}

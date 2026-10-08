package com.moyue.social.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 评论实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_comment")
public class Comment extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作品 ID */
    private Long bookId;

    /** 章节 ID（章内评论，可空） */
    private Long chapterId;

    /** 评论人（身份来自网关注入头） */
    private Long userId;

    /** 回复的评论 ID（楼中楼） */
    private Long replyTo;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 审核状态：0 正常 / 1 待审核 / 2 已下架（运营端治理） */
    private Integer status;

    /** 运营置顶：0 否 / 1 是 */
    private Integer top;
}

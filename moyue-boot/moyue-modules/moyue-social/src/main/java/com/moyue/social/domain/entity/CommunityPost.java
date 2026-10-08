package com.moyue.social.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 社区帖子实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_community_post")
public class CommunityPost extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发布用户 ID */
    private Long userId;

    /** 发布用户名 */
    private String userName;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 话题标签 */
    private String topic;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 状态：0 待审 / 1 已发 / 2 下架 */
    private Integer status;
}

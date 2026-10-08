package com.moyue.social.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区帖子查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityPostQuery extends PageQuery {

    /** 标题，模糊匹配 */
    private String title;

    /** 话题标签，模糊匹配 */
    private String topic;

    /** 状态：0 待审 / 1 已发 / 2 下架 */
    private Integer status;

    /** 发布用户 */
    private Long userId;
}

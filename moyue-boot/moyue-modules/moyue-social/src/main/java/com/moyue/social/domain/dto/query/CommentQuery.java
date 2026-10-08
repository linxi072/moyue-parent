package com.moyue.social.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论分页查询（按 book_id 聚合）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentQuery extends PageQuery {

    /** 作品 ID */
    private Long bookId;

    /** 章节 ID（可空） */
    private Long chapterId;

    /** 评论人 */
    private Long userId;

    /** 审核状态（运营端过滤）：0 正常 / 1 待审核 / 2 已下架 */
    private Integer status;

    /** 是否置顶（运营端过滤）：0 否 / 1 是 */
    private Integer top;
}

package com.moyue.content.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 章节分页查询。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChapterQuery extends PageQuery {

    /** 所属作品 */
    private Long bookId;

    /** 状态：0 草稿 / 1 已发布 / 2 定时发布（空=全部） */
    private Integer status;

    /** 标题模糊 */
    private String title;
}

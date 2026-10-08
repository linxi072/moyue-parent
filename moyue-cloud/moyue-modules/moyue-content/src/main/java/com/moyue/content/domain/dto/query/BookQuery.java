package com.moyue.content.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作品查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookQuery extends PageQuery {

    /** 书名，模糊匹配 */
    private String title;

    /** 作者名，模糊匹配 */
    private String authorName;

    /** 状态：0 连载中 / 1 已完结 / 2 已下架 */
    private Integer status;

    /** 分类 */
    private Long categoryId;
}

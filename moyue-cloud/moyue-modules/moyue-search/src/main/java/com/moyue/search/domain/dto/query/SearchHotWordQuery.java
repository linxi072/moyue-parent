package com.moyue.search.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 搜索热词查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchHotWordQuery extends PageQuery {

    /** 热词，模糊匹配 */
    private String word;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}

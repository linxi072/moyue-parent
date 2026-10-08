package com.moyue.search.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 屏蔽词查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BlockWordQuery extends PageQuery {

    /** 屏蔽词，模糊匹配 */
    private String word;

    /** 级别：1 拦截 / 2 告警 */
    private Integer level;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}

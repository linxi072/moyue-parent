package com.moyue.risk.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 敏感词查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SensitiveWordQuery extends PageQuery {

    /** 模糊匹配词 */
    private String word;

    /** 级别：1 拦截 / 2 告警 */
    private Integer level;

    /** 状态：0 停用 / 1 启用 */
    private Integer enabled;
}

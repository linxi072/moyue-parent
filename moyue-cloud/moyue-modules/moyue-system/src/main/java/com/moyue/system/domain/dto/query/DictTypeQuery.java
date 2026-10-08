package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictTypeQuery extends PageQuery {

    /** 字典名称，模糊匹配 */
    private String dictName;

    /** 字典类型，模糊匹配 */
    private String dictType;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}

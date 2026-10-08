package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDataQuery extends PageQuery {

    /** 字典类型 */
    private String dictType;

    /** 字典标签，模糊匹配 */
    private String dictLabel;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}

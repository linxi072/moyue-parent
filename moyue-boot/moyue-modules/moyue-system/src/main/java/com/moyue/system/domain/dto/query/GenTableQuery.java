package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码生成配置查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GenTableQuery extends PageQuery {

    /** 表名称，模糊匹配 */
    private String tableName;

    /** 表描述，模糊匹配 */
    private String tableComment;
}

package com.moyue.system.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参数配置查询条件。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConfigQuery extends PageQuery {

    /** 参数名称，模糊匹配 */
    private String configName;

    /** 参数键名，模糊匹配 */
    private String configKey;

    /** 类型：0 自定义 / 1 系统内置 */
    private Integer configType;
}

package com.moyue.system.domain.dto.request;

import com.moyue.system.domain.entity.GenTable;
import com.moyue.system.domain.entity.GenTableColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 代码生成配置保存入参：基本信息 + 字段映射。
 *
 * <p>Controller 不允许出现两个 {@code @RequestBody}，故平铺为单一对象。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GenConfigRequest extends GenTable {

    /** 字段映射列表 */
    private List<GenTableColumn> columns;
}

package com.moyue.system.domain.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表单 Schema 保存入参（设计器拖拽结果）。
 *
 * <p>Schema = 整体配置（config）+ 组件树（items）。items 为整包替换语义：
 * 保存即先清空该表单原有字段项，再按传入顺序重建，避免拖拽删除的字段残留。
 *
 * @author moyue
 */
@Data
public class FormSchemaRequest {

    /** 表单整体配置（布局 / 校验 / 提交地址等） */
    private Map<String, Object> config;

    /** 组件树 */
    private List<Map<String, Object>> items;
}

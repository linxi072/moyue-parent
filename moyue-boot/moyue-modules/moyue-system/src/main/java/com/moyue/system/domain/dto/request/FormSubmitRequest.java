package com.moyue.system.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 【渲染端】表单提交入参。
 *
 * <p>字段值以 {@code itemKey -> value} 扁平承载，后端按表单定义做白名单过滤，
 * 未定义的 key 一律丢弃，避免脏数据写入。
 *
 * @author moyue
 */
@Data
public class FormSubmitRequest {

    /** 表单 ID */
    @NotNull(message = "formId 不能为空")
    private Long formId;

    /** 提交内容 */
    private Map<String, Object> data;
}

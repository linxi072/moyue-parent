package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageQuery;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.request.FormSchemaRequest;
import com.moyue.system.domain.entity.SysForm;
import com.moyue.system.domain.entity.SysFormData;
import com.moyue.system.domain.entity.SysFormItem;
import com.moyue.system.service.SysFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线构建器（⑯）管理端：16 个端点。
 *
 * <p>渲染端提交端点（POST /api/v1/system/forms/{formId}/submit）在
 * {@link SysFormRenderController}，因其不走 /admin 前缀。
 *
 * @author moyue
 */
@Tag(name = "在线构建器", description = "动态表单定义、Schema 设计、版本管理与收集数据")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/forms")
@RequiredArgsConstructor
public class SysFormController {

    private final SysFormService formService;

    @Operation(summary = "表单分页列表")
    @RequiresPermissions("system:form:list")
    @GetMapping
    public R<PageResult<SysForm>> page(@RequestParam(required = false) String formName,
                                       @RequestParam(required = false) Integer status,
                                       PageQuery query) {
        return R.ok(formService.pageForms(formName, status, (int) query.getPage(), (int) query.getSize()));
    }

    @Operation(summary = "新建表单", description = "form_key 唯一，初始为草稿")
    @RequiresPermissions("system:form:add")
    @Log(title = "在线构建器", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SysForm entity) {
        return R.ok(formService.createForm(entity));
    }

    @Operation(summary = "表单详情")
    @RequiresPermissions("system:form:query")
    @GetMapping("/{formId}")
    public R<SysForm> detail(@PathVariable Long formId) {
        return R.ok(formService.detail(formId));
    }

    @Operation(summary = "编辑表单基本信息", description = "form_key / status / version 不可经此接口变更")
    @RequiresPermissions("system:form:edit")
    @Log(title = "在线构建器", businessType = BusinessType.UPDATE)
    @PutMapping("/{formId}")
    public R<Boolean> update(@PathVariable Long formId, @RequestBody SysForm entity) {
        entity.setId(formId);
        return R.ok(formService.updateForm(entity));
    }

    @Operation(summary = "删除表单", description = "已收集数据时需传 force=true 二次确认")
    @RequiresPermissions("system:form:remove")
    @Log(title = "在线构建器", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formId}")
    public R<Boolean> delete(@PathVariable Long formId,
                             @RequestParam(required = false, defaultValue = "false") boolean force) {
        return R.ok(formService.deleteForm(formId, force));
    }

    @Operation(summary = "读取表单 Schema", description = "整体配置 + 组件树，供设计器回显")
    @RequiresPermissions("system:form:query")
    @GetMapping("/{formId}/schema")
    public R<Map<String, Object>> getSchema(@PathVariable Long formId) {
        return R.ok(formService.getSchema(formId));
    }

    @Operation(summary = "保存表单 Schema", description = "items 整包替换，设计器里删掉的字段会被清除")
    @RequiresPermissions("system:form:design")
    @Log(title = "在线构建器", businessType = BusinessType.UPDATE)
    @PutMapping("/{formId}/schema")
    public R<Boolean> saveSchema(@PathVariable Long formId, @RequestBody FormSchemaRequest request) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("config", request.getConfig());
        schema.put("items", request.getItems());
        return R.ok(formService.saveSchema(formId, schema));
    }

    @Operation(summary = "复制表单", description = "含 Schema，副本为草稿，标识自动加 _copy_ 时间戳后缀")
    @RequiresPermissions("system:form:add")
    @Log(title = "在线构建器", businessType = BusinessType.INSERT)
    @PostMapping("/{formId}/copy")
    public R<Long> copy(@PathVariable Long formId) {
        return R.ok(formService.copyForm(formId));
    }

    @Operation(summary = "发布 / 停用", description = "status：0 草稿 / 1 发布 / 2 下线；发布会留版本快照并 +1 版本")
    @RequiresPermissions("system:form:publish")
    @Log(title = "在线构建器", businessType = BusinessType.UPDATE)
    @PutMapping("/{formId}/status")
    public R<Boolean> changeStatus(@PathVariable Long formId, @RequestParam Integer status) {
        return R.ok(formService.changeStatus(formId, status));
    }

    @Operation(summary = "预览渲染结果", description = "返回 Schema 供前端渲染引擎加载")
    @RequiresPermissions("system:form:query")
    @GetMapping("/{formId}/preview")
    public R<Map<String, Object>> preview(@PathVariable Long formId) {
        return R.ok(formService.preview(formId));
    }

    @Operation(summary = "Schema 历史版本列表")
    @RequiresPermissions("system:form:query")
    @GetMapping("/{formId}/history")
    public R<List<Map<String, Object>>> history(@PathVariable Long formId) {
        return R.ok(formService.history(formId));
    }

    @Operation(summary = "回滚到指定历史版本", description = "回滚前会把当前版本自动留档，保证可回退")
    @RequiresPermissions("system:form:publish")
    @Log(title = "在线构建器", businessType = BusinessType.UPDATE)
    @PostMapping("/{formId}/history/{versionId}/rollback")
    public R<Boolean> rollback(@PathVariable Long formId, @PathVariable Long versionId) {
        return R.ok(formService.rollback(formId, versionId));
    }

    @Operation(summary = "收集数据列表")
    @RequiresPermissions("system:form:data")
    @GetMapping("/{formId}/data")
    public R<PageResult<SysFormData>> pageData(@PathVariable Long formId, PageQuery query) {
        return R.ok(formService.pageData(formId, (int) query.getPage(), (int) query.getSize()));
    }

    @Operation(summary = "单条提交数据详情")
    @RequiresPermissions("system:form:data")
    @GetMapping("/{formId}/data/{dataId}")
    public R<SysFormData> dataDetail(@PathVariable Long formId, @PathVariable Long dataId) {
        return R.ok(formService.dataDetail(formId, dataId));
    }

    @Operation(summary = "删除单条提交数据")
    @RequiresPermissions("system:form:data")
    @Log(title = "在线构建器", businessType = BusinessType.DELETE)
    @DeleteMapping("/{formId}/data/{dataId}")
    public R<Boolean> deleteData(@PathVariable Long formId, @PathVariable Long dataId) {
        return R.ok(formService.deleteData(formId, dataId));
    }

    @Operation(summary = "导出收集数据", description = "CSV 格式，列按字段项排序展开动态字段")
    @RequiresPermissions("system:form:export")
    @Log(title = "在线构建器", businessType = BusinessType.EXPORT)
    @GetMapping("/{formId}/data/export")
    public void export(@PathVariable Long formId, HttpServletResponse response) throws IOException {
        SysForm form = formService.detail(formId);
        List<SysFormItem> items = formService.listItems(formId);
        List<SysFormData> rows = formService.listDataForExport(formId);

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename="
                + URLEncoder.encode("form_" + form.getFormKey(), StandardCharsets.UTF_8) + ".csv");
        try (PrintWriter writer = response.getWriter()) {
            // BOM：Excel 依赖它识别 UTF-8
            writer.write('\ufeff');
            List<String> header = new ArrayList<>();
            header.add("提交时间");
            header.add("提交人");
            header.add("提交 IP");
            for (SysFormItem item : items) {
                header.add(item.getItemName());
            }
            writer.println(String.join(",", header.stream().map(SysFormController::escape).toList()));
            for (SysFormData row : rows) {
                Map<String, Object> data = parseData(row.getDataJson());
                List<String> cells = new ArrayList<>();
                cells.add(row.getSubmitTime() == null ? "" : row.getSubmitTime().toString());
                cells.add(row.getSubmitName() == null ? "" : row.getSubmitName());
                cells.add(row.getSubmitIp() == null ? "" : row.getSubmitIp());
                for (SysFormItem item : items) {
                    Object value = data.get(item.getItemKey());
                    cells.add(value == null ? "" : String.valueOf(value));
                }
                writer.println(String.join(",", cells.stream().map(SysFormController::escape).toList()));
            }
            writer.flush();
        }
    }

    /** 提交内容 JSON → Map；解析失败时返回空 Map，导出不因单条脏数据中断 */
    private Map<String, Object> parseData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}

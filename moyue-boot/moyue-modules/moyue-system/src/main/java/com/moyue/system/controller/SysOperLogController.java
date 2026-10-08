package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.query.OperLogQuery;
import com.moyue.system.domain.entity.SysOperLog;
import com.moyue.system.service.SysOperLogService;
import com.moyue.system.util.CsvUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 操作日志（⑦）：7 个端点。写入由 {@code @Log} 切面自动采集，本控制器只负责查询与清理。
 *
 * @author moyue
 */
@Tag(name = "操作日志", description = "操作日志查询、清理、导出与统计")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/operlogs")
@RequiredArgsConstructor
public class SysOperLogController {

    private static final String[] EXPORT_FIELDS = {
            "id", "title", "businessType", "operName", "operUrl", "operIp",
            "requestMethod", "status", "costTime", "operTime"
    };

    private final SysOperLogService operLogService;

    @Operation(summary = "操作日志分页")
    @RequiresPermissions("system:operlog:list")
    @GetMapping
    public R<PageResult<SysOperLog>> page(OperLogQuery query) {
        return R.ok(operLogService.pageLogs(query));
    }

    @Operation(summary = "操作详情", description = "含请求参数与返回结果的脱敏展示")
    @RequiresPermissions("system:operlog:query")
    @GetMapping("/{operId}")
    public R<SysOperLog> detail(@PathVariable Long operId) {
        return R.ok(operLogService.detail(operId));
    }

    @Operation(summary = "删除单条")
    @RequiresPermissions("system:operlog:remove")
    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{operId}")
    public R<Boolean> delete(@PathVariable Long operId) {
        return R.ok(operLogService.delete(operId));
    }

    @Operation(summary = "批量删除")
    @RequiresPermissions("system:operlog:remove")
    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @DeleteMapping
    public R<Boolean> deleteBatch(@RequestBody List<Long> ids) {
        return R.ok(operLogService.deleteBatch(ids));
    }

    @Operation(summary = "清空全部", description = "需二次确认，仅超管可操作")
    @RequiresPermissions("system:operlog:clear")
    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clear")
    public R<Integer> clear() {
        return R.ok(operLogService.clear());
    }

    @Operation(summary = "导出查询结果")
    @RequiresPermissions("system:operlog:export")
    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(OperLogQuery query, HttpServletResponse response) throws IOException {
        CsvUtils.write(response, "operlog", operLogService.listForExport(query), EXPORT_FIELDS);
    }

    @Operation(summary = "操作量统计", description = "按日 / 按模块聚合，供看板使用")
    @RequiresPermissions("system:operlog:list")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(OperLogQuery query) {
        return R.ok(operLogService.stats(query));
    }
}

package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.entity.SysOperLogEntity;
import com.moyue.system.service.OperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 操作日志接口。
 * 完整前缀 /api/v1/admin/system/operlog，删除需 system:operlog:remove 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/operlog")
public class SysOperLogController {

    @Autowired
    private OperLogService operLogService;

    /** 操作日志分页：GET /api/v1/admin/system/operlog */
    @GetMapping
    public R<PageResult<SysOperLogEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String module,
                                                @RequestParam(required = false) String operatorName,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) String beginTime,
                                                @RequestParam(required = false) String endTime) {
        return R.ok(operLogService.page(page, size, module, operatorName, status,
                parseTime(beginTime), parseTime(endTime)));
    }

    /** 批量删除（物理删除，逗号分隔 ID）：DELETE /api/v1/admin/system/operlog/{ids} */
    @DeleteMapping("/{ids}")
    @RequiresPermissions("system:operlog:remove")
    @Log(module = "操作日志", businessType = Log.BusinessType.DELETE)
    public R<Void> delete(@PathVariable String ids) {
        operLogService.deleteByIds(parseIds(ids));
        return R.ok();
    }

    // ====================== 工具 ======================

    /** 逗号分隔 ID 串解析为 List<Long>（非法输入抛参数异常） */
    private List<Long> parseIds(String ids) {
        try {
            return Stream.of(ids.split(","))
                    .filter(s -> !s.isBlank())
                    .map(String::trim)
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /** ISO 时间串（yyyy-MM-ddTHH:mm:ss）解析，失败返回 null 忽略过滤条件 */
    private LocalDateTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

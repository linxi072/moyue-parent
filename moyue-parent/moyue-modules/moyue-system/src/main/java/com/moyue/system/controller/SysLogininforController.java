package com.moyue.system.controller;

import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.core.annotation.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.entity.SysLogininforEntity;
import com.moyue.system.service.LogininforService;
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
 * 登录日志接口。
 * 完整前缀 /api/v1/admin/system/logininfor，删除/清空需 system:logininfor:* 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/logininfor")
public class SysLogininforController {

    @Autowired
    private LogininforService logininforService;

    /** 登录日志分页：GET /api/v1/admin/system/logininfor */
    @GetMapping
    public R<PageResult<SysLogininforEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String username,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) String beginTime,
                                                   @RequestParam(required = false) String endTime) {
        return R.ok(logininforService.page(page, size, username, status,
                parseTime(beginTime), parseTime(endTime)));
    }

    /** 批量删除（物理删除，逗号分隔 ID）：DELETE /api/v1/admin/system/logininfor/{ids} */
    @DeleteMapping("/{ids}")
    @RequiresPermissions("system:logininfor:remove")
    @Log(module = "登录日志", businessType = Log.BusinessType.DELETE)
    public R<Void> delete(@PathVariable String ids) {
        logininforService.deleteByIds(parseIds(ids));
        return R.ok();
    }

    /** 清空登录日志：DELETE /api/v1/admin/system/logininfor/clear */
    @DeleteMapping("/clear")
    @RequiresPermissions("system:logininfor:clear")
    @Log(module = "登录日志", businessType = Log.BusinessType.DELETE)
    public R<Void> clear() {
        logininforService.clear();
        return R.ok();
    }

    // ====================== 工具 ======================

    /** 逗号分隔 ID 串解析为 List<Long>（非法输入返回空集合） */
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

    /** ISO 时间串解析，失败返回 null 忽略过滤条件 */
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

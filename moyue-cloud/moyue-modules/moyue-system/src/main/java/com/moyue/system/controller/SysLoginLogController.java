package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.query.LoginLogQuery;
import com.moyue.system.domain.entity.SysLoginLog;
import com.moyue.system.service.SysLoginLogService;
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

/**
 * 登录日志（⑧）：8 个端点。仅追加，不提供修改接口。
 *
 * @author moyue
 */
@Tag(name = "登录日志", description = "登录审计查询、清理、导出与账号解锁")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/loginlogs")
@RequiredArgsConstructor
public class SysLoginLogController {

    private static final String[] EXPORT_FIELDS = {
            "id", "username", "ip", "location", "browser", "os", "status", "message", "loginTime"
    };

    private final SysLoginLogService loginLogService;

    @Operation(summary = "登录日志分页")
    @RequiresPermissions("system:loginlog:list")
    @GetMapping
    public R<PageResult<SysLoginLog>> page(LoginLogQuery query) {
        return R.ok(loginLogService.pageLogs(query));
    }

    @Operation(summary = "登录详情")
    @RequiresPermissions("system:loginlog:query")
    @GetMapping("/{infoId}")
    public R<SysLoginLog> detail(@PathVariable Long infoId) {
        return R.ok(loginLogService.detail(infoId));
    }

    @Operation(summary = "删除单条")
    @RequiresPermissions("system:loginlog:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoId}")
    public R<Boolean> delete(@PathVariable Long infoId) {
        return R.ok(loginLogService.removeById(infoId));
    }

    @Operation(summary = "批量删除")
    @RequiresPermissions("system:loginlog:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping
    public R<Boolean> deleteBatch(@RequestBody List<Long> ids) {
        return R.ok(loginLogService.deleteBatch(ids));
    }

    @Operation(summary = "清空全部", description = "需二次确认")
    @RequiresPermissions("system:loginlog:clear")
    @Log(title = "登录日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clear")
    public R<Integer> clear() {
        return R.ok(loginLogService.clear());
    }

    @Operation(summary = "导出查询结果")
    @RequiresPermissions("system:loginlog:export")
    @Log(title = "登录日志", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(LoginLogQuery query, HttpServletResponse response) throws IOException {
        CsvUtils.write(response, "loginlog", loginLogService.listForExport(query), EXPORT_FIELDS);
    }

    @Operation(summary = "按账号查登录记录", description = "含失败次数，供风控查看")
    @RequiresPermissions("system:loginlog:query")
    @GetMapping("/account/{username}")
    public R<List<SysLoginLog>> byAccount(@PathVariable String username) {
        return R.ok(loginLogService.listByAccount(username));
    }

    @Operation(summary = "解锁账号", description = "清除连续失败锁定计数")
    @RequiresPermissions("system:loginlog:unlock")
    @Log(title = "登录日志", businessType = BusinessType.UPDATE)
    @GetMapping("/unlock/{username}")
    public R<Boolean> unlock(@PathVariable String username) {
        return R.ok(loginLogService.unlock(username));
    }
}

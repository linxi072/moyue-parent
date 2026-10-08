package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.query.GenTableQuery;
import com.moyue.system.domain.dto.request.GenConfigRequest;
import com.moyue.system.domain.entity.GenTable;
import com.moyue.system.domain.entity.GenTableColumn;
import com.moyue.system.service.GenTableService;
import com.moyue.system.util.PageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 代码生成域（⑮）：10 个端点。
 *
 * <p>链路：information_schema 导入表结构 → 生成配置 → Velocity 渲染 → Zip 下载 / 写本地路径。
 * 写路径端点限 dev 环境（架构说明书 12.1 D-19），生成结果不自动进版本库。
 *
 * @author moyue
 */
@Slf4j
@Tag(name = "代码生成", description = "表结构导入、生成配置维护与代码预览下载")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/generator")
@RequiredArgsConstructor
public class SysGenController {

    private final GenTableService genTableService;

    @Operation(summary = "数据库表清单", description = "含是否已导入标记，支持按表名过滤")
    @RequiresPermissions("tool:gen:list")
    @GetMapping("/tables")
    public R<List<Map<String, Object>>> tables(@RequestParam(required = false) String tableName) {
        return R.ok(genTableService.listDbTables(tableName));
    }

    @Operation(summary = "导入表结构", description = "已导入的表会自动跳过")
    @RequiresPermissions("tool:gen:import")
    @Log(title = "代码生成", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public R<Integer> importTables(@RequestBody List<String> tableNames) {
        return R.ok(genTableService.importTables(tableNames));
    }

    @Operation(summary = "生成配置列表")
    @RequiresPermissions("tool:gen:list")
    @GetMapping("/configs")
    public R<PageResult<GenTable>> configs(GenTableQuery query) {
        return R.ok(PageUtils.toResult(genTableService.lambdaQuery()
                .like(StringUtils.isNotBlank(query.getTableName()), GenTable::getTableName, query.getTableName())
                .like(StringUtils.isNotBlank(query.getTableComment()), GenTable::getTableComment, query.getTableComment())
                .orderByDesc(GenTable::getCreateTime)
                .page(PageUtils.page(query))));
    }

    @Operation(summary = "配置详情", description = "基本信息 + 字段列表")
    @RequiresPermissions("tool:gen:query")
    @GetMapping("/configs/{tableId}")
    public R<Map<String, Object>> configDetail(@PathVariable Long tableId) {
        GenTable table = genTableService.detail(tableId);
        List<GenTableColumn> columns = genTableService.listColumns(tableId);
        return R.ok(Map.of("table", table, "columns", columns));
    }

    @Operation(summary = "更新生成配置", description = "模块名 / 业务名 / 包名 / 作者 / 功能名 / 字段映射")
    @RequiresPermissions("tool:gen:edit")
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @PutMapping("/configs")
    public R<Boolean> updateConfig(@RequestBody GenConfigRequest request) {
        return R.ok(genTableService.updateConfig(request, request.getColumns()));
    }

    @Operation(summary = "删除生成配置", description = "连带删除字段配置")
    @RequiresPermissions("tool:gen:remove")
    @Log(title = "代码生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/configs/{tableId}")
    public R<Boolean> deleteConfig(@PathVariable Long tableId) {
        return R.ok(genTableService.deleteConfig(tableId));
    }

    @Operation(summary = "同步最新表结构", description = "字段增删改后刷新，返回变更字段数")
    @RequiresPermissions("tool:gen:edit")
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @PostMapping("/sync/{tableId}")
    public R<Integer> sync(@PathVariable Long tableId) {
        return R.ok(genTableService.syncTable(tableId));
    }

    @Operation(summary = "代码预览", description = "返回待生成文件的路径与内容映射")
    @RequiresPermissions("tool:gen:preview")
    @GetMapping("/preview/{tableId}")
    public R<Map<String, String>> preview(@PathVariable Long tableId) {
        return R.ok(genTableService.preview(tableId));
    }

    @Operation(summary = "打包下载", description = "Zip 流式下载，不落盘")
    @RequiresPermissions("tool:gen:download")
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @PostMapping("/download/{tableId}")
    public void download(@PathVariable Long tableId, HttpServletResponse response) throws IOException {
        GenTable table = genTableService.detail(tableId);
        byte[] zip = genTableService.download(tableId);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename="
                + URLEncoder.encode(table.getClassName(), StandardCharsets.UTF_8) + ".zip");
        response.getOutputStream().write(zip);
    }

    @Operation(summary = "生成到服务本地路径", description = "限 dev 环境，生产调用直接拒绝（架构说明书 D-19）")
    @RequiresPermissions("tool:gen:code")
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @PostMapping("/generate/{tableId}")
    public R<List<String>> generate(@PathVariable Long tableId,
                                    @RequestParam(required = false) String genPath) {
        try {
            return R.ok(genTableService.generateToPath(tableId, genPath));
        } catch (BusinessException e) {
            // 环境限制属于预期内的拒绝，按业务码透传，避免被全局异常处理成 500
            if (e.getCode() == ErrorCode.FORBIDDEN.getCode()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, e.getMessage());
            }
            throw e;
        }
    }
}

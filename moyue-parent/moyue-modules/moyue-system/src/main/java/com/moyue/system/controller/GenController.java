package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.service.gen.GenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 代码生成接口。
 * 完整前缀 /api/v1/admin/system/gen，预览/下载需 system:gen:* 权限码；
 * 下载（写型生成动作）落操作日志，预览为查询不落日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/gen")
public class GenController {

    @Autowired
    private GenService genService;

    /** 数据库表清单：GET /api/v1/admin/system/gen/tables */
    @GetMapping("/tables")
    public R<List<Map<String, Object>>> tables() {
        return R.ok(genService.listTables());
    }

    /** 表字段元数据：GET /api/v1/admin/system/gen/columns/{table} */
    @GetMapping("/columns/{table}")
    public R<List<GenService.GenColumn>> columns(@PathVariable String table) {
        return R.ok(genService.getColumns(table));
    }

    /** 生成预览（文件名 → 内容，查询接口不落操作日志）：GET /api/v1/admin/system/gen/preview/{table} */
    @GetMapping("/preview/{table}")
    @RequiresPermissions("system:gen:preview")
    public R<Map<String, String>> preview(@PathVariable String table) {
        return R.ok(genService.preview(table));
    }

    /** 打包下载 zip：POST /api/v1/admin/system/gen/download/{table} */
    @PostMapping("/download/{table}")
    @RequiresPermissions("system:gen:download")
    @Log(module = "代码生成", businessType = Log.BusinessType.GENCODE)
    public ResponseEntity<byte[]> download(@PathVariable String table) {
        byte[] zip = genService.downloadZip(table);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=moyue-gen-" + table + ".zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }
}

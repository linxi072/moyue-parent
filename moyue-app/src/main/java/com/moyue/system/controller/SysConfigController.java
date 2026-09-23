package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.entity.SysConfigEntity;
import com.moyue.system.service.SysConfigService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统参数接口。
 * 完整前缀 /api/v1/admin/system/config，写操作需 system:config:* 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/config")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    /** 参数分页（可选 keyword）：GET /api/v1/admin/system/config */
    @GetMapping
    public R<PageResult<SysConfigEntity>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword) {
        return R.ok(sysConfigService.page(page, size, keyword));
    }

    /** 参数详情：GET /api/v1/admin/system/config/{id} */
    @GetMapping("/{id}")
    public R<SysConfigEntity> get(@PathVariable Long id) {
        return R.ok(sysConfigService.get(id));
    }

    /** 按键名取参数（缓存命中）：GET /api/v1/admin/system/config/key/{key} */
    @GetMapping("/key/{key}")
    public R<SysConfigEntity> getByKey(@PathVariable String key) {
        return R.ok(sysConfigService.getByKey(key));
    }

    /** 新建参数：POST /api/v1/admin/system/config */
    @PostMapping
    @RequiresPermissions("system:config:add")
    @Log(module = "参数设置", businessType = Log.BusinessType.INSERT)
    public R<SysConfigEntity> create(@RequestBody ConfigReq req) {
        return R.ok(sysConfigService.create(req.getConfigName(), req.getConfigKey(),
                req.getConfigValue(), req.getIsSystem(), req.getRemark()));
    }

    /** 编辑参数：PUT /api/v1/admin/system/config/{id} */
    @PutMapping("/{id}")
    @RequiresPermissions("system:config:edit")
    @Log(module = "参数设置", businessType = Log.BusinessType.UPDATE)
    public R<SysConfigEntity> update(@PathVariable Long id, @RequestBody ConfigReq req) {
        return R.ok(sysConfigService.update(id, req.getConfigName(), req.getConfigKey(),
                req.getConfigValue(), req.getRemark()));
    }

    /** 删除参数（内置不可删）：DELETE /api/v1/admin/system/config/{id} */
    @DeleteMapping("/{id}")
    @RequiresPermissions("system:config:remove")
    @Log(module = "参数设置", businessType = Log.BusinessType.DELETE)
    public R<Void> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return R.ok();
    }

    /** 刷新参数缓存：PUT /api/v1/admin/system/config/refreshCache */
    @PutMapping("/refreshCache")
    @RequiresPermissions("system:config:refresh")
    @Log(module = "参数设置", businessType = Log.BusinessType.UPDATE)
    public R<Void> refreshCache() {
        sysConfigService.refreshCache();
        return R.ok();
    }

    // ====================== 请求体 ======================

    /** 参数请求体（编辑时仅更新非空字段；isSystem 仅创建时可指定） */
    @Data
    public static class ConfigReq {
        private String configName;
        private String configKey;
        private String configValue;
        private Integer isSystem;
        private String remark;
    }
}

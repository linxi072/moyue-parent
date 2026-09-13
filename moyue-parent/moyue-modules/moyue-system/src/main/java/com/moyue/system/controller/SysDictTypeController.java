package com.moyue.system.controller;

import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.core.annotation.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.entity.SysDictDataEntity;
import com.moyue.system.entity.SysDictTypeEntity;
import com.moyue.system.service.SysDictService;
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

import java.util.List;

/**
 * 字典类型接口。
 * 完整前缀 /api/v1/admin/system/dict，写操作需 system:dict:* 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/dict")
public class SysDictTypeController {

    @Autowired
    private SysDictService sysDictService;

    // ====================== 字典类型 ======================

    /** 字典类型分页：GET /api/v1/admin/system/dict/types */
    @GetMapping("/types")
    public R<PageResult<SysDictTypeEntity>> listTypes(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer status) {
        return R.ok(sysDictService.pageTypes(page, size, keyword, status));
    }

    /** 字典类型详情：GET /api/v1/admin/system/dict/types/{id} */
    @GetMapping("/types/{id}")
    public R<SysDictTypeEntity> getType(@PathVariable Long id) {
        return R.ok(sysDictService.getType(id));
    }

    /** 新建字典类型：POST /api/v1/admin/system/dict/types */
    @PostMapping("/types")
    @RequiresPermissions("system:dict:add")
    @Log(module = "字典管理", businessType = Log.BusinessType.INSERT)
    public R<SysDictTypeEntity> createType(@RequestBody DictTypeReq req) {
        return R.ok(sysDictService.createType(req.getDictName(), req.getDictType(), req.getStatus(), req.getRemark()));
    }

    /** 编辑字典类型：PUT /api/v1/admin/system/dict/types/{id} */
    @PutMapping("/types/{id}")
    @RequiresPermissions("system:dict:edit")
    @Log(module = "字典管理", businessType = Log.BusinessType.UPDATE)
    public R<SysDictTypeEntity> updateType(@PathVariable Long id, @RequestBody DictTypeReq req) {
        return R.ok(sysDictService.updateType(id, req.getDictName(), req.getDictType(), req.getStatus(), req.getRemark()));
    }

    /** 删除字典类型（联动删数据）：DELETE /api/v1/admin/system/dict/types/{id} */
    @DeleteMapping("/types/{id}")
    @RequiresPermissions("system:dict:remove")
    @Log(module = "字典管理", businessType = Log.BusinessType.DELETE)
    public R<Void> deleteType(@PathVariable Long id) {
        sysDictService.deleteType(id);
        return R.ok();
    }

    /** 按类型取启用字典数据（缓存命中）：GET /api/v1/admin/system/dict/types/{type}/data */
    @GetMapping("/types/{type}/data")
    public R<List<SysDictDataEntity>> listDataByType(@PathVariable String type) {
        return R.ok(sysDictService.listDataByType(type));
    }

    // ====================== 缓存刷新 ======================

    /** 刷新字典缓存：PUT /api/v1/admin/system/dict/refreshCache */
    @PutMapping("/refreshCache")
    @RequiresPermissions("system:dict:refresh")
    @Log(module = "字典管理", businessType = Log.BusinessType.UPDATE)
    public R<Void> refreshCache() {
        sysDictService.refreshCache();
        return R.ok();
    }

    // ====================== 请求体 ======================

    /** 字典类型请求体（编辑时仅更新非空字段） */
    @Data
    public static class DictTypeReq {
        private String dictName;
        private String dictType;
        private Integer status;
        private String remark;
    }
}

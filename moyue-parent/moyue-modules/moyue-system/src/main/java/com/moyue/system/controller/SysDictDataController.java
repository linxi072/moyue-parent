package com.moyue.system.controller;

import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.RequiresPermissions;
import com.moyue.system.annotation.Log;
import com.moyue.system.entity.SysDictDataEntity;
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

/**
 * 字典数据接口。
 * 完整前缀 /api/v1/admin/system/dict，写操作需 system:dict:* 权限码并落操作日志。
 */
@RestController
@RequestMapping("/api/v1/admin/system/dict")
public class SysDictDataController {

    @Autowired
    private SysDictService sysDictService;

    /** 字典数据分页（可选 dictType / status）：GET /api/v1/admin/system/dict/data */
    @GetMapping("/data")
    public R<PageResult<SysDictDataEntity>> listData(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) String dictType,
                                                     @RequestParam(required = false) Integer status) {
        return R.ok(sysDictService.pageData(page, size, dictType, status));
    }

    /** 字典数据详情：GET /api/v1/admin/system/dict/data/{id} */
    @GetMapping("/data/{id}")
    public R<SysDictDataEntity> getData(@PathVariable Long id) {
        return R.ok(sysDictService.getData(id));
    }

    /** 新建字典数据：POST /api/v1/admin/system/dict/data */
    @PostMapping("/data")
    @RequiresPermissions("system:dict:add")
    @Log(module = "字典管理", businessType = Log.BusinessType.INSERT)
    public R<SysDictDataEntity> createData(@RequestBody DictDataReq req) {
        return R.ok(sysDictService.createData(req.getDictType(), req.getDictLabel(), req.getDictValue(),
                req.getDictSort(), req.getIsDefault(), req.getStatus(), req.getRemark()));
    }

    /** 编辑字典数据：PUT /api/v1/admin/system/dict/data/{id} */
    @PutMapping("/data/{id}")
    @RequiresPermissions("system:dict:edit")
    @Log(module = "字典管理", businessType = Log.BusinessType.UPDATE)
    public R<SysDictDataEntity> updateData(@PathVariable Long id, @RequestBody DictDataReq req) {
        return R.ok(sysDictService.updateData(id, req.getDictLabel(), req.getDictValue(),
                req.getDictSort(), req.getIsDefault(), req.getStatus(), req.getRemark()));
    }

    /** 删除字典数据：DELETE /api/v1/admin/system/dict/data/{id} */
    @DeleteMapping("/data/{id}")
    @RequiresPermissions("system:dict:remove")
    @Log(module = "字典管理", businessType = Log.BusinessType.DELETE)
    public R<Void> deleteData(@PathVariable Long id) {
        sysDictService.deleteData(id);
        return R.ok();
    }

    // ====================== 请求体 ======================

    /** 字典数据请求体（编辑时仅更新非空字段；dictType 创建后不可变更） */
    @Data
    public static class DictDataReq {
        private String dictType;
        private String dictLabel;
        private String dictValue;
        private Integer dictSort;
        private Integer isDefault;
        private Integer status;
        private String remark;
    }
}

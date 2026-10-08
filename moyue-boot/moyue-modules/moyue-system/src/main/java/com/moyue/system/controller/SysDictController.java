package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.query.DictDataQuery;
import com.moyue.system.domain.dto.query.DictTypeQuery;
import com.moyue.system.domain.entity.SysDictData;
import com.moyue.system.domain.entity.SysDictType;
import com.moyue.system.domain.vo.DictDataVO;
import com.moyue.system.domain.vo.DictTypeVO;
import com.moyue.system.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典管理（⑤）：9 个端点（字典类型 4 + 字典数据 4 + 按类型查 1）。
 *
 * @author moyue
 */
@Tag(name = "字典管理", description = "字典类型与字典数据，变更主动失效 Redis 缓存")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    // -------------------------------------------------- 字典类型

    @Operation(summary = "字典类型分页")
    @RequiresPermissions("system:dict:list")
    @GetMapping("/types")
    public R<PageResult<DictTypeVO>> pageTypes(DictTypeQuery query) {
        return R.ok(dictService.pageDictTypes(query));
    }

    @Operation(summary = "新建字典类型", description = "dict_type 唯一")
    @RequiresPermissions("system:dict:add")
    @Log(title = "字典管理", businessType = BusinessType.INSERT)
    @PostMapping("/types")
    public R<Long> createType(@RequestBody SysDictType entity) {
        return R.ok(dictService.createDictType(entity));
    }

    @Operation(summary = "编辑字典类型")
    @RequiresPermissions("system:dict:edit")
    @Log(title = "字典管理", businessType = BusinessType.UPDATE)
    @PutMapping("/types/{dictId}")
    public R<Boolean> updateType(@PathVariable Long dictId, @RequestBody SysDictType entity) {
        entity.setId(dictId);
        return R.ok(dictService.updateDictType(entity));
    }

    @Operation(summary = "删除字典类型", description = "级联删除其下字典数据")
    @RequiresPermissions("system:dict:remove")
    @Log(title = "字典管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/types/{dictId}")
    public R<Boolean> deleteType(@PathVariable Long dictId) {
        return R.ok(dictService.deleteDictType(dictId));
    }

    // -------------------------------------------------- 字典数据

    @Operation(summary = "字典数据分页")
    @RequiresPermissions("system:dict:list")
    @GetMapping("/data")
    public R<PageResult<DictDataVO>> pageData(DictDataQuery query) {
        return R.ok(dictService.pageDictData(query));
    }

    @Operation(summary = "新建字典数据")
    @RequiresPermissions("system:dict:add")
    @Log(title = "字典管理", businessType = BusinessType.INSERT)
    @PostMapping("/data")
    public R<Long> createData(@RequestBody SysDictData entity) {
        return R.ok(dictService.createDictData(entity));
    }

    @Operation(summary = "编辑字典数据")
    @RequiresPermissions("system:dict:edit")
    @Log(title = "字典管理", businessType = BusinessType.UPDATE)
    @PutMapping("/data/{dictCode}")
    public R<Boolean> updateData(@PathVariable Long dictCode, @RequestBody SysDictData entity) {
        entity.setId(dictCode);
        return R.ok(dictService.updateDictData(entity));
    }

    @Operation(summary = "删除字典数据")
    @RequiresPermissions("system:dict:remove")
    @Log(title = "字典管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/data/{dictCode}")
    public R<Boolean> deleteData(@PathVariable Long dictCode) {
        return R.ok(dictService.deleteDictData(dictCode));
    }

    @Operation(summary = "按类型查字典数据", description = "启动加载进 Redis，变更主动失效")
    @GetMapping("/data/type/{dictType}")
    public R<List<DictDataVO>> dataByType(@PathVariable String dictType) {
        return R.ok(dictService.listDataByType(dictType));
    }
}

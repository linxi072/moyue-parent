package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.entity.SysDept;
import com.moyue.system.domain.vo.DeptVO;
import com.moyue.system.service.SysDeptService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理（④）：4 个端点。
 *
 * @author moyue
 */
@Tag(name = "部门管理", description = "部门树增删改查，维护 ancestors 供数据权限使用")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @Operation(summary = "部门树")
    @RequiresPermissions("system:dept:list")
    @GetMapping
    public R<List<DeptVO>> tree(@RequestParam(required = false) String deptName,
                                 @RequestParam(required = false) Integer status) {
        return R.ok(deptService.listDeptTree(deptName, status));
    }

    @Operation(summary = "新建部门")
    @RequiresPermissions("system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SysDept entity) {
        return R.ok(deptService.createDept(entity));
    }

    @Operation(summary = "编辑部门", description = "父部门变化时级联重建子孙 ancestors")
    @RequiresPermissions("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SysDept entity) {
        entity.setId(id);
        return R.ok(deptService.updateDept(entity));
    }

    @Operation(summary = "删除部门", description = "存在子部门或已分配用户时拒删")
    @RequiresPermissions("system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(deptService.deleteDept(id));
    }
}

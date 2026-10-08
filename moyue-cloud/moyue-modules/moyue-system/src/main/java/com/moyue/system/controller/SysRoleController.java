package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.query.RoleQuery;
import com.moyue.system.domain.entity.SysRole;
import com.moyue.system.domain.vo.RoleVO;
import com.moyue.system.service.SysRoleService;
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
 * 角色管理（②）：6 个端点。
 *
 * @author moyue
 */
@Tag(name = "角色管理", description = "角色增删改查与菜单授权")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "角色分页")
    @RequiresPermissions("system:role:list")
    @GetMapping
    public R<PageResult<RoleVO>> page(RoleQuery query) {
        return R.ok(roleService.pageRoles(query));
    }

    @Operation(summary = "新建角色", description = "role_key 唯一")
    @RequiresPermissions("system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SysRole entity) {
        return R.ok(roleService.createRole(entity));
    }

    @Operation(summary = "编辑角色")
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SysRole entity) {
        entity.setId(id);
        return R.ok(roleService.updateRole(entity));
    }

    @Operation(summary = "删除角色", description = "级联清理角色-菜单、角色-部门、用户-角色关联")
    @RequiresPermissions("system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(roleService.deleteRole(id));
    }

    @Operation(summary = "查询角色已授权菜单")
    @RequiresPermissions("system:role:query")
    @GetMapping("/{roleId}/menus")
    public R<List<Long>> menus(@PathVariable Long roleId) {
        return R.ok(roleService.listMenuIdsByRole(roleId));
    }

    @Operation(summary = "设置角色菜单", description = "先清后插，单事务完成")
    @RequiresPermissions("system:role:grant")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/{roleId}/menus")
    public R<Boolean> assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        return R.ok(roleService.assignMenus(roleId, menuIds));
    }

    // 【架构缺口 G-10】说明书 ②角色管理只定义了 menus 授权端点，
    // 但 sys_role.data_scope = 2（自定义数据）必须能圈定部门，否则该枚举形同虚设。

    @Operation(summary = "查询角色数据权限部门")
    @RequiresPermissions("system:role:query")
    @GetMapping("/{roleId}/depts")
    public R<List<Long>> depts(@PathVariable Long roleId) {
        return R.ok(roleService.listDeptIdsByRole(roleId));
    }

    @Operation(summary = "设置角色数据权限部门", description = "先清后插，data_scope=2 时生效")
    @RequiresPermissions("system:role:grant")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/{roleId}/depts")
    public R<Boolean> assignDepts(@PathVariable Long roleId, @RequestBody List<Long> deptIds) {
        return R.ok(roleService.assignDepts(roleId, deptIds));
    }
}

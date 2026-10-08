package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.common.security.context.UserContext;
import com.moyue.system.domain.dto.query.MenuQuery;
import com.moyue.system.domain.entity.SysMenu;
import com.moyue.system.domain.vo.MenuVO;
import com.moyue.system.service.SysMenuService;
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
 * 菜单管理（③）：5 个端点。
 *
 * @author moyue
 */
@Tag(name = "菜单管理", description = "菜单（目录 / 菜单 / 按钮）树增删改查")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "菜单树")
    @RequiresPermissions("system:menu:list")
    @GetMapping
    public R<List<MenuVO>> tree(MenuQuery query) {
        return R.ok(menuService.listMenuTree(query));
    }

    @Operation(summary = "新建菜单")
    @RequiresPermissions("system:menu:add")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SysMenu entity) {
        return R.ok(menuService.createMenu(entity));
    }

    @Operation(summary = "编辑菜单")
    @RequiresPermissions("system:menu:edit")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SysMenu entity) {
        entity.setId(id);
        return R.ok(menuService.updateMenu(entity));
    }

    @Operation(summary = "删除菜单", description = "存在子菜单时拒删")
    @RequiresPermissions("system:menu:remove")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(menuService.deleteMenu(id));
    }

    @Operation(summary = "当前登录者可见菜单树", description = "未登录返回 10002；超管返回全量")
    @GetMapping("/current")
    public R<List<MenuVO>> current() {
        var user = UserContext.get();
        if (user == null) {
            return R.fail(com.moyue.common.core.exception.ErrorCode.UNAUTHORIZED);
        }
        boolean superAdmin = Boolean.TRUE.equals(user.getSuperAdmin());
        return R.ok(menuService.listCurrentMenuTree(user.getRoleKeys(), superAdmin));
    }
}

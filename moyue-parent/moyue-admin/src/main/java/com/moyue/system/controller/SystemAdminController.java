package com.moyue.system.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.system.entity.SysDeptEntity;
import com.moyue.system.entity.SysMenuEntity;
import com.moyue.system.entity.SysRoleEntity;
import com.moyue.system.service.SystemService;
import com.moyue.system.vo.UserVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理后台接口（部门 / 菜单 / 角色 / 用户 + 绑定）。
 * 完整前缀 /api/v1/admin/system，由 AdminRoleInterceptor（moyue-common）做 role=3 断言。
 */
@RestController
@RequestMapping("/api/v1/admin/system")
public class SystemAdminController {

    @Autowired
    private SystemService systemService;

    // ====================== 部门 ======================

    /** 部门树：GET /api/v1/admin/system/depts */
    @GetMapping("/depts")
    public R<List<SysDeptEntity>> listDepts() {
        return R.ok(systemService.listDepts());
    }

    /** 部门详情：GET /api/v1/admin/system/depts/{id} */
    @GetMapping("/depts/{id}")
    public R<SysDeptEntity> getDept(@PathVariable Long id) {
        return R.ok(systemService.getDept(id));
    }

    /** 新建部门：POST /api/v1/admin/system/depts */
    @PostMapping("/depts")
    public R<SysDeptEntity> createDept(@RequestBody DeptReq req) {
        return R.ok(systemService.createDept(req.getParentId(), req.getDeptName(), req.getOrderNum(),
                req.getLeader(), req.getPhone(), req.getEmail(), req.getStatus()));
    }

    /** 编辑部门：PUT /api/v1/admin/system/depts/{id} */
    @PutMapping("/depts/{id}")
    public R<SysDeptEntity> updateDept(@PathVariable Long id, @RequestBody DeptReq req) {
        return R.ok(systemService.updateDept(id, req.getParentId(), req.getDeptName(), req.getOrderNum(),
                req.getLeader(), req.getPhone(), req.getEmail(), req.getStatus()));
    }

    /** 删除部门（需无子部门、无用户）：DELETE /api/v1/admin/system/depts/{id} */
    @DeleteMapping("/depts/{id}")
    public R<Void> deleteDept(@PathVariable Long id) {
        systemService.deleteDept(id);
        return R.ok();
    }

    // ====================== 菜单 ======================

    /** 菜单树：GET /api/v1/admin/system/menus */
    @GetMapping("/menus")
    public R<List<SysMenuEntity>> listMenus() {
        return R.ok(systemService.listMenus());
    }

    /** 菜单详情：GET /api/v1/admin/system/menus/{id} */
    @GetMapping("/menus/{id}")
    public R<SysMenuEntity> getMenu(@PathVariable Long id) {
        return R.ok(systemService.getMenu(id));
    }

    /** 新建菜单：POST /api/v1/admin/system/menus */
    @PostMapping("/menus")
    public R<SysMenuEntity> createMenu(@RequestBody MenuReq req) {
        return R.ok(systemService.createMenu(req.getParentId(), req.getMenuName(), req.getMenuType(),
                req.getPath(), req.getComponent(), req.getPerms(), req.getIcon(), req.getOrderNum(), req.getStatus()));
    }

    /** 编辑菜单：PUT /api/v1/admin/system/menus/{id} */
    @PutMapping("/menus/{id}")
    public R<SysMenuEntity> updateMenu(@PathVariable Long id, @RequestBody MenuReq req) {
        return R.ok(systemService.updateMenu(id, req.getParentId(), req.getMenuName(), req.getMenuType(),
                req.getPath(), req.getComponent(), req.getPerms(), req.getIcon(), req.getOrderNum(), req.getStatus()));
    }

    /** 删除菜单（需无子菜单）：DELETE /api/v1/admin/system/menus/{id} */
    @DeleteMapping("/menus/{id}")
    public R<Void> deleteMenu(@PathVariable Long id) {
        systemService.deleteMenu(id);
        return R.ok();
    }

    /** 当前管理员菜单树（由网关透传 X-User-Id 推导角色→菜单）：GET /api/v1/admin/system/menus/current */
    @GetMapping("/menus/current")
    public R<List<SysMenuEntity>> currentMenus(@RequestHeader(value = Constants.USER_ID_HEADER, required = false) Long userId) {
        return R.ok(systemService.getCurrentMenus(userId));
    }

    // ====================== 角色 ======================

    /** 角色列表（可选 status 过滤）：GET /api/v1/admin/system/roles */
    @GetMapping("/roles")
    public R<List<SysRoleEntity>> listRoles(@RequestParam(required = false) Integer status) {
        return R.ok(systemService.listRoles(status));
    }

    /** 角色详情：GET /api/v1/admin/system/roles/{id} */
    @GetMapping("/roles/{id}")
    public R<SysRoleEntity> getRole(@PathVariable Long id) {
        return R.ok(systemService.getRole(id));
    }

    /** 新建角色：POST /api/v1/admin/system/roles */
    @PostMapping("/roles")
    public R<SysRoleEntity> createRole(@RequestBody RoleReq req) {
        return R.ok(systemService.createRole(req.getRoleName(), req.getRoleKey(), req.getDataScope(),
                req.getDeptIds(), req.getStatus()));
    }

    /** 编辑角色：PUT /api/v1/admin/system/roles/{id} */
    @PutMapping("/roles/{id}")
    public R<SysRoleEntity> updateRole(@PathVariable Long id, @RequestBody RoleReq req) {
        return R.ok(systemService.updateRole(id, req.getRoleName(), req.getRoleKey(), req.getDataScope(),
                req.getDeptIds(), req.getStatus()));
    }

    /** 删除角色（需未分配给用户）：DELETE /api/v1/admin/system/roles/{id} */
    @DeleteMapping("/roles/{id}")
    public R<Void> deleteRole(@PathVariable Long id) {
        systemService.deleteRole(id);
        return R.ok();
    }

    /** 角色已授权菜单 ID：GET /api/v1/admin/system/roles/{roleId}/menus */
    @GetMapping("/roles/{roleId}/menus")
    public R<List<Long>> getRoleMenus(@PathVariable Long roleId) {
        return R.ok(systemService.getRoleMenuIds(roleId));
    }

    /** 设置角色菜单（先清后插）：PUT /api/v1/admin/system/roles/{roleId}/menus */
    @PutMapping("/roles/{roleId}/menus")
    public R<Void> setRoleMenus(@PathVariable Long roleId, @RequestBody MenuBindReq req) {
        systemService.setRoleMenus(roleId, req.getMenuIds());
        return R.ok();
    }

    // ====================== 用户 ======================

    /** 用户分页（可选 deptId / keyword）：GET /api/v1/admin/system/users */
    @GetMapping("/users")
    public R<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) Long deptId,
                                           @RequestParam(required = false) String keyword) {
        return R.ok(systemService.listUsers(page, size, deptId, keyword));
    }

    /** 用户详情：GET /api/v1/admin/system/users/{id} */
    @GetMapping("/users/{id}")
    public R<UserVO> getUser(@PathVariable Long id) {
        return R.ok(systemService.getUser(id));
    }

    /** 新建用户：POST /api/v1/admin/system/users */
    @PostMapping("/users")
    public R<UserVO> createUser(@RequestBody UserReq req) {
        return R.ok(systemService.createUser(req.getDeptId(), req.getUsername(), req.getNickname(),
                req.getPassword(), req.getEmail(), req.getPhone(), req.getStatus(), req.getRoleIds()));
    }

    /** 编辑用户：PUT /api/v1/admin/system/users/{id} */
    @PutMapping("/users/{id}")
    public R<UserVO> updateUser(@PathVariable Long id, @RequestBody UserReq req) {
        return R.ok(systemService.updateUser(id, req.getDeptId(), req.getUsername(), req.getNickname(),
                req.getPassword(), req.getEmail(), req.getPhone(), req.getStatus(), req.getRoleIds()));
    }

    /** 删除用户：DELETE /api/v1/admin/system/users/{id} */
    @DeleteMapping("/users/{id}")
    public R<Void> deleteUser(@PathVariable Long id) {
        systemService.deleteUser(id);
        return R.ok();
    }

    /** 用户已分配角色 ID：GET /api/v1/admin/system/users/{userId}/roles */
    @GetMapping("/users/{userId}/roles")
    public R<List<Long>> getUserRoles(@PathVariable Long userId) {
        return R.ok(systemService.getUserRoleIds(userId));
    }

    /** 设置用户角色（先清后插）：PUT /api/v1/admin/system/users/{userId}/roles */
    @PutMapping("/users/{userId}/roles")
    public R<Void> setUserRoles(@PathVariable Long userId, @RequestBody RoleBindReq req) {
        systemService.setUserRoles(userId, req.getRoleIds());
        return R.ok();
    }

    // ====================== 请求体 ======================

    /** 部门请求体（字段均可选，编辑时仅更新非空字段） */
    @Data
    public static class DeptReq {
        private Long parentId;
        private String deptName;
        private Integer orderNum;
        private String leader;
        private String phone;
        private String email;
        private Integer status;
    }

    /** 菜单请求体（字段均可选） */
    @Data
    public static class MenuReq {
        private Long parentId;
        private String menuName;
        private Integer menuType;
        private String path;
        private String component;
        private String perms;
        private String icon;
        private Integer orderNum;
        private Integer status;
    }

    /** 角色请求体（字段均可选） */
    @Data
    public static class RoleReq {
        private String roleName;
        private String roleKey;
        private Integer dataScope;
        private String deptIds;
        private Integer status;
    }

    /** 用户请求体（编辑时 password 为空则不修改；roleIds 为空则不改动角色） */
    @Data
    public static class UserReq {
        private Long deptId;
        private String username;
        private String nickname;
        private String password;
        private String email;
        private String phone;
        private Integer status;
        private List<Long> roleIds;
    }

    /** 角色-菜单绑定请求体 */
    @Data
    public static class MenuBindReq {
        private List<Long> menuIds;
    }

    /** 用户-角色绑定请求体 */
    @Data
    public static class RoleBindReq {
        private List<Long> roleIds;
    }
}

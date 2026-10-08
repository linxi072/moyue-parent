package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.common.security.context.UserContext;
import com.moyue.system.domain.dto.UserSaveRequest;
import com.moyue.system.domain.dto.query.UserQuery;
import com.moyue.system.domain.entity.SysUser;
import com.moyue.system.domain.vo.UserProfileVO;
import com.moyue.system.domain.vo.UserVO;
import com.moyue.system.service.SysUserService;
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
import java.util.Map;

/**
 * 用户管理（①）：10 个端点。
 *
 * @author moyue
 */
@Tag(name = "用户管理", description = "统一主体（读者 / 作者 / 运营）的增删改查与授权")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "用户分页")
    @RequiresPermissions("system:user:list")
    @GetMapping
    public R<PageResult<UserVO>> page(UserQuery query) {
        return R.ok(userService.pageUsers(query));
    }

    @Operation(summary = "用户详情")
    @RequiresPermissions("system:user:query")
    @GetMapping("/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(userService.getUserById(id));
    }

    @Operation(summary = "新建用户", description = "密码 BCrypt 加密后落库")
    @RequiresPermissions("system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT, saveResponseData = false)
    @PostMapping
    public R<Long> create(@Validated @RequestBody UserSaveRequest request) {
        return R.ok(userService.createUser(request, request.getRoleIds()));
    }

    @Operation(summary = "编辑用户", description = "密码为空表示不修改；roleIds 为 null 表示不调整角色")
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE, saveResponseData = false)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @Validated @RequestBody UserSaveRequest request) {
        request.setId(id);
        return R.ok(userService.updateUser(request, request.getRoleIds()));
    }

    @Operation(summary = "删除用户")
    @RequiresPermissions("system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(userService.deleteUser(id));
    }

    @Operation(summary = "查询用户已分配角色")
    @RequiresPermissions("system:user:query")
    @GetMapping("/{userId}/roles")
    public R<List<Long>> roles(@PathVariable Long userId) {
        return R.ok(userService.listRoleIdsByUser(userId));
    }

    @Operation(summary = "设置用户角色", description = "先清后插，单事务完成")
    @RequiresPermissions("system:user:grant")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/{userId}/roles")
    public R<Boolean> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        return R.ok(userService.assignRoles(userId, roleIds));
    }

    @Operation(summary = "启停用户")
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{userId}/status")
    public R<Boolean> status(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        return R.ok(userService.changeStatus(userId, body.get("status")));
    }

    @Operation(summary = "重置密码")
    @RequiresPermissions("system:user:resetPwd")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/{userId}/reset-pwd")
    public R<Boolean> resetPwd(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        return R.ok(userService.resetPassword(userId, body.get("password")));
    }

    // ------------------------------------------------------------------
    // 个人中心（【架构缺口 G-11】补齐）：路径为字面量，优先于 /{id} 匹配
    // ------------------------------------------------------------------

    @Operation(summary = "当前登录者资料", description = "含角色标识与权限串，前端据此做按钮级显隐")
    @GetMapping("/profile")
    public R<UserProfileVO> profile() {
        return R.ok(userService.getProfile(UserContext.getUserId()));
    }

    @Operation(summary = "修改个人资料", description = "仅昵称 / 手机 / 邮箱 / 头像 / 备注可改，其余字段忽略")
    @Log(title = "个人中心", businessType = BusinessType.UPDATE)
    @PutMapping("/profile")
    public R<Boolean> updateProfile(@RequestBody SysUser patch) {
        return R.ok(userService.updateProfile(UserContext.getUserId(), patch));
    }

    @Operation(summary = "修改密码", description = "需校验原密码，新密码 8 ~ 20 位")
    @Log(title = "个人中心", businessType = BusinessType.UPDATE, saveRequestData = false)
    @PutMapping("/profile/password")
    public R<Boolean> updatePassword(@RequestBody Map<String, String> body) {
        return R.ok(userService.changePassword(
                UserContext.getUserId(), body.get("oldPassword"), body.get("newPassword")));
    }
}

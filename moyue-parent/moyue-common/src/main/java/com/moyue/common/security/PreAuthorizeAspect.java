package com.moyue.common.security;

import com.moyue.common.exception.BizException;
import com.moyue.common.core.annotation.PermissionProvider;
import com.moyue.common.core.annotation.RequiresPermissions;
import com.moyue.common.core.annotation.RequiresRoles;
import com.moyue.common.core.domain.ResultCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 权限注解切面（对齐 RuoYi PreAuthorizeAspect）：
 * 方法进入前校验 {@link RequiresRoles} / {@link RequiresPermissions}，
 * 校验失败抛 {@link BizException}（FORBIDDEN 10003），由 GlobalExceptionHandler 统一转为 R.fail。
 *
 * <p>类级与方法级注解同时存在时，方法级优先；多个注解并存时全部通过才放行。</p>
 */
@Aspect
@Component
public class PreAuthorizeAspect {

    private final PermissionProvider permissionProvider;

    @Autowired
    public PreAuthorizeAspect(PermissionProvider permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    @Before("@annotation(requiresRoles) || @within(requiresRoles)")
    public void checkRoles(JoinPoint joinPoint, RequiresRoles requiresRoles) {
        Integer role = SecurityContextHolder.currentRole();
        String roleStr = role == null ? null : String.valueOf(role);
        boolean pass = roleStr != null
                && Arrays.stream(requiresRoles.value()).anyMatch(r -> r.equals(roleStr));
        if (!pass) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    @Before("@annotation(requiresPermissions) || @within(requiresPermissions)")
    public void checkPermissions(JoinPoint joinPoint, RequiresPermissions requiresPermissions) {
        SecurityContextHolder.LoginUser user = SecurityContextHolder.get();
        Set<String> perms = permissionProvider.permissionsOf(
                user == null ? null : user.getUserId(), user == null ? null : user.getRole());
        boolean pass = perms.contains(PermissionProvider.ALL_PERMISSIONS)
                || Arrays.stream(requiresPermissions.value()).anyMatch(perms::contains);
        if (!pass) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}

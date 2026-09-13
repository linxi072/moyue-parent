package com.moyue.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限码校验注解（对齐 RuoYi @RequiresPermissions）：标注在 Controller 方法或类上，
 * 由 {@link PreAuthorizeAspect} 校验当前用户权限集合（{@link PermissionProvider} 提供）。
 *
 * <pre>{@code
 * @RequiresPermissions("system:user:list")
 * }</pre>
 *
 * <p>权限集合包含 {@code *:*:*}（超级管理员通配，RuoYi 惯例）即放行；
 * 默认 {@link DefaultPermissionProvider} 按角色授予：role=3 → *:*:*，其余为空集。
 * 接入系统管理 RBAC 后可替换为按用户查菜单权限的真实 Provider。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermissions {

    /** 允许访问的权限码集合（任一命中即放行） */
    String[] value();
}

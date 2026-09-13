package com.moyue.common.security;

import java.util.Set;

/**
 * 权限集合提供者：返回当前登录用户的权限码集合。
 * 默认实现按角色授予通配权（role=3 → *:*:*）；接入 RBAC 后替换为真实实现（注册同名 Bean 即覆盖）。
 */
public interface PermissionProvider {

    /** 超级管理员通配权限码（RuoYi 惯例） */
    String ALL_PERMISSIONS = "*:*:*";

    /** @return 当前用户权限码集合；匿名用户返回空集 */
    Set<String> permissionsOf(Long userId, Integer role);
}

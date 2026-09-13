package com.moyue.common.security;

import com.moyue.common.core.annotation.PermissionProvider;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 默认权限提供者：按角色授予（role=3 管理员 → 通配权 *:*:*，其余角色无细粒度权限码）。
 * 系统管理 RBAC 接入后，用真实实现（按用户查角色-菜单权限）注册为 {@link PermissionProvider} Bean 即覆盖本实现。
 */
@Component
public class DefaultPermissionProvider implements PermissionProvider {

    @Override
    public Set<String> permissionsOf(Long userId, Integer role) {
        if (userId == null) {
            return Set.of();
        }
        if (Integer.valueOf(3).equals(role)) {
            return Set.of(ALL_PERMISSIONS);
        }
        return Set.of();
    }
}

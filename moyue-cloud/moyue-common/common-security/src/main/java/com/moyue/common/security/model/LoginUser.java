package com.moyue.common.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * 登录主体（JWT 载荷镜像）。
 *
 * <p>架构说明书 3.x 采用 {@code sys_user} 统一主体，{@code user_type}（1 读者 / 2 作家 / 3 运营）
 * 与后台角色两层解耦：本类同时承载两者，{@code userType} 决定可访问端，
 * {@code roleKeys} / {@code permissions} 决定后台可操作范围。
 *
 * @author moyue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 用户名/昵称 */
    private String username;

    /** 主体类型：1 读者 / 2 作家 / 3 运营，见 Constants.USER_TYPE_* */
    private Integer userType;

    /** 部门 ID，用于数据权限过滤 */
    private Long deptId;

    /** 后台角色标识集合，如 ROLE_ADMIN / content:auditor */
    private Set<String> roleKeys;

    /** 后台权限标识集合，如 system:user:list */
    private Set<String> permissions;

    /** 是否超管（超管放行所有权限校验） */
    private Boolean superAdmin;

    /**
     * 是否拥有指定角色（超管恒为 true）。
     *
     * @param role 角色标识
     * @return 是否拥有
     */
    public boolean hasRole(String role) {
        if (Boolean.TRUE.equals(superAdmin)) {
            return true;
        }
        return roleKeys != null && roleKeys.contains(role);
    }

    /**
     * 是否拥有指定权限（超管恒为 true）。
     *
     * @param permission 权限标识
     * @return 是否拥有
     */
    public boolean hasPermission(String permission) {
        if (Boolean.TRUE.equals(superAdmin)) {
            return true;
        }
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 是否后台运营人员。
     *
     * @return userType == 3
     */
    public boolean isOperator() {
        return userType != null && userType == 3;
    }

    public Set<String> getRoleKeys() {
        return roleKeys == null ? Collections.emptySet() : roleKeys;
    }

    public Set<String> getPermissions() {
        return permissions == null ? Collections.emptySet() : permissions;
    }
}

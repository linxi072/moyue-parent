package com.moyue.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.auth.domain.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证数据访问：用户查询 + 角色 / 权限关联查询。
 *
 * <p>角色与权限用一条 SQL 关联查回，避免 auth 侧引入角色、菜单两套实体 —— 认证链路
 * 只需要「角色标识集合」与「权限字符串集合」这两个结果。
 *
 * @author moyue
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {

    /**
     * 按账号或手机号查用户（C 端支持手机号登录）。
     */
    @Select("""
            SELECT * FROM sys_user
            WHERE is_deleted = 0
              AND (username = #{account} OR phone = #{account})
            LIMIT 1
            """)
    AuthUser selectByAccount(@Param("account") String account);

    /**
     * 查用户角色标识集合。
     */
    @Select("""
            SELECT r.role_key FROM sys_role r
              INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.is_deleted = 0 AND r.status = 1
            """)
    List<String> selectRoleKeys(@Param("userId") Long userId);

    /**
     * 查用户权限字符串集合（超管由调用方按角色判定，不在此处展开全部菜单）。
     */
    @Select("""
            SELECT DISTINCT m.perms FROM sys_menu m
              INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
              INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
            WHERE ur.user_id = #{userId}
              AND m.is_deleted = 0
              AND m.perms IS NOT NULL AND m.perms <> ''
            """)
    List<String> selectPerms(@Param("userId") Long userId);
}

package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.RoleQuery;
import com.moyue.system.domain.entity.SysRole;
import com.moyue.system.domain.vo.RoleVO;

import java.util.List;

/**
 * 角色管理域（②）服务。
 *
 * @author moyue
 */
public interface SysRoleService extends IService<SysRole> {

    /** 角色分页（不分页时用 listRoles） */
    PageResult<RoleVO> pageRoles(RoleQuery query);

    /** 全部角色 */
    List<RoleVO> listRoles(RoleQuery query);

    /** 新建角色（role_key 唯一） */
    Long createRole(SysRole entity);

    /** 编辑角色 */
    boolean updateRole(SysRole entity);

    /** 删除角色（同时清理角色-菜单、角色-部门、用户-角色关联） */
    boolean deleteRole(Long id);

    /** 查询角色已授权菜单 ID */
    List<Long> listMenuIdsByRole(Long roleId);

    /** 设置角色菜单（先清后插） */
    boolean assignMenus(Long roleId, List<Long> menuIds);

    /**
     * 角色已授权部门 ID（data_scope = 2 自定义数据时生效）。
     *
     * @param roleId 角色 ID
     * @return 部门 ID 列表
     */
    List<Long> listDeptIdsByRole(Long roleId);

    /**
     * 设置角色数据权限部门（先清后插）。
     *
     * @param roleId  角色 ID
     * @param deptIds 部门 ID 列表
     * @return 是否成功
     */
    boolean assignDepts(Long roleId, List<Long> deptIds);

    /** 按角色 ID 查角色标识集合，供鉴权使用 */
    List<String> listRoleKeysByUser(Long userId);
}

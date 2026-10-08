package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.system.domain.dto.query.MenuQuery;
import com.moyue.system.domain.entity.SysMenu;
import com.moyue.system.domain.vo.MenuVO;

import java.util.List;
import java.util.Set;

/**
 * 菜单管理域（③）服务。
 *
 * @author moyue
 */
public interface SysMenuService extends IService<SysMenu> {

    /** 菜单树（全量，按 order_num 排序） */
    List<MenuVO> listMenuTree(MenuQuery query);

    /** 新建菜单 */
    Long createMenu(SysMenu entity);

    /** 编辑菜单 */
    boolean updateMenu(SysMenu entity);

    /** 删除菜单（存在子菜单时拒删） */
    boolean deleteMenu(Long id);

    /** 当前登录者可见菜单树（超管返回全量） */
    List<MenuVO> listCurrentMenuTree(Set<String> roleKeys, boolean superAdmin);

    /** 按角色标识集合查权限标识集合 */
    Set<String> listPermsByRoleKeys(Set<String> roleKeys);

    /** 按菜单 ID 查权限标识集合 */
    Set<String> listPermsByMenuIds(List<Long> menuIds);
}

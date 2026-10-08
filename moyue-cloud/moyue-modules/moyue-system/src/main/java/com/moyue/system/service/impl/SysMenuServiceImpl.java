package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.system.domain.dto.query.MenuQuery;
import com.moyue.system.domain.entity.SysMenu;
import com.moyue.system.domain.entity.SysRole;
import com.moyue.system.domain.entity.SysRoleMenu;
import com.moyue.system.domain.vo.MenuVO;
import com.moyue.system.mapper.SysMenuMapper;
import com.moyue.system.mapper.SysRoleMapper;
import com.moyue.system.mapper.SysRoleMenuMapper;
import com.moyue.system.service.SysMenuService;
import com.moyue.system.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单管理域（③）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<MenuVO> listMenuTree(MenuQuery query) {
        List<MenuVO> list = list(buildWrapper(query)).stream().map(this::toVO).collect(Collectors.toList());
        return TreeUtils.buildMenuTree(list, 0L);
    }

    @Override
    public Long createMenu(SysMenu entity) {
        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        if (entity.getOrderNum() == null) {
            entity.setOrderNum(0);
        }
        if (!StringUtils.hasText(entity.getMenuType())) {
            entity.setMenuType("C");
        }
        if (entity.getVisible() == null) {
            entity.setVisible(1);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(Constants.STATUS_ENABLE);
        }
        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateMenu(SysMenu entity) {
        if (getById(entity.getId()) == null) {
            throw BusinessException.notFound("菜单");
        }
        // 父节点不能是自己或自己的子孙，否则树会成环
        if (entity.getParentId() != null && entity.getParentId().equals(entity.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上级菜单不能是当前菜单");
        }
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMenu(Long id) {
        if (getById(id) == null) {
            throw BusinessException.notFound("菜单");
        }
        long children = lambdaQuery().eq(SysMenu::getParentId, id).count();
        if (children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在子菜单，不允许删除");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        return removeById(id);
    }

    @Override
    public List<MenuVO> listCurrentMenuTree(Set<String> roleKeys, boolean superAdmin) {
        List<SysMenu> menus;
        if (superAdmin) {
            menus = lambdaQuery()
                    .in(SysMenu::getMenuType, List.of("M", "C"))
                    .eq(SysMenu::getStatus, Constants.STATUS_ENABLE)
                    .orderByAsc(SysMenu::getOrderNum)
                    .list();
        } else if (roleKeys == null || roleKeys.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<Long> roleIds = roleMapper.selectList(
                            new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleKey, roleKeys))
                    .stream().map(SysRole::getId).toList();
            if (roleIds.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                    .stream().map(SysRoleMenu::getMenuId).distinct().toList();
            if (menuIds.isEmpty()) {
                return Collections.emptyList();
            }
            menus = lambdaQuery()
                    .in(SysMenu::getId, menuIds)
                    .in(SysMenu::getMenuType, List.of("M", "C"))
                    .eq(SysMenu::getStatus, Constants.STATUS_ENABLE)
                    .orderByAsc(SysMenu::getOrderNum)
                    .list();
        }
        List<MenuVO> list = menus.stream().map(this::toVO).collect(Collectors.toList());
        return TreeUtils.buildMenuTree(list, 0L);
    }

    @Override
    public Set<String> listPermsByRoleKeys(Set<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> roleIds = roleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleKey, roleKeys))
                .stream().map(SysRole::getId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> menuIds = roleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        return listPermsByMenuIds(menuIds);
    }

    @Override
    public Set<String> listPermsByMenuIds(List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> perms = new HashSet<>();
        for (SysMenu menu : lambdaQuery().in(SysMenu::getId, menuIds).list()) {
            if (StringUtils.hasText(menu.getPerms())) {
                perms.add(menu.getPerms());
            }
        }
        return perms;
    }

    private LambdaQueryWrapper<SysMenu> buildWrapper(MenuQuery query) {
        return new LambdaQueryWrapper<SysMenu>()
                .like(StringUtils.hasText(query.getMenuName()), SysMenu::getMenuName, query.getMenuName())
                .eq(query.getStatus() != null, SysMenu::getStatus, query.getStatus())
                .orderByAsc(SysMenu::getOrderNum);
    }

    private MenuVO toVO(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setMenuName(menu.getMenuName());
        vo.setParentId(menu.getParentId());
        vo.setOrderNum(menu.getOrderNum());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setQuery(menu.getQuery());
        vo.setIsFrame(menu.getIsFrame());
        vo.setIsCache(menu.getIsCache());
        vo.setMenuType(menu.getMenuType());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        vo.setCreateTime(menu.getCreateTime());
        vo.setRemark(menu.getRemark());
        return vo;
    }
}

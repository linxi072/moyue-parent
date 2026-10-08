package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.RoleQuery;
import com.moyue.system.domain.entity.SysRole;
import com.moyue.system.domain.entity.SysRoleDept;
import com.moyue.system.domain.entity.SysRoleMenu;
import com.moyue.system.domain.entity.SysUserRole;
import com.moyue.system.domain.vo.RoleVO;
import com.moyue.system.mapper.SysRoleDeptMapper;
import com.moyue.system.mapper.SysRoleMapper;
import com.moyue.system.mapper.SysRoleMenuMapper;
import com.moyue.system.mapper.SysUserRoleMapper;
import com.moyue.system.service.SysRoleService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理域（②）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<RoleVO> pageRoles(RoleQuery query) {
        var page = PageUtils.<SysRole>page(query);
        var result = page(page, buildWrapper(query));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    public List<RoleVO> listRoles(RoleQuery query) {
        return list(buildWrapper(query)).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(SysRole entity) {
        checkRoleKeyUnique(entity.getRoleKey(), null);
        if (entity.getRoleSort() == null) {
            entity.setRoleSort(0);
        }
        if (entity.getDataScope() == null) {
            entity.setDataScope(1);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateRole(SysRole entity) {
        SysRole exist = getById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("角色");
        }
        checkRoleKeyUnique(entity.getRoleKey(), entity.getId());
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long id) {
        SysRole exist = getById(id);
        if (exist == null) {
            throw BusinessException.notFound("角色");
        }
        if (id != null && id == 1L) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置超管角色不可删除");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        return removeById(id);
    }

    @Override
    public List<Long> listMenuIdsByRole(Long roleId) {
        List<SysRoleMenu> list = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(SysRoleMenu::getMenuId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return true;
        }
        List<SysRoleMenu> rows = new ArrayList<>(menuIds.size());
        for (Long menuId : menuIds) {
            SysRoleMenu row = new SysRoleMenu();
            row.setRoleId(roleId);
            row.setMenuId(menuId);
            rows.add(row);
        }
        rows.forEach(roleMenuMapper::insert);
        return true;
    }

    @Override
    public List<Long> listDeptIdsByRole(Long roleId) {
        return roleDeptMapper
                .selectList(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId))
                .stream().map(SysRoleDept::getDeptId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
        if (deptIds == null || deptIds.isEmpty()) {
            return true;
        }
        List<SysRoleDept> rows = new ArrayList<>(deptIds.size());
        for (Long deptId : deptIds) {
            SysRoleDept row = new SysRoleDept();
            row.setRoleId(roleId);
            row.setDeptId(deptId);
            rows.add(row);
        }
        rows.forEach(roleDeptMapper::insert);
        return true;
    }

    @Override
    public List<String> listRoleKeysByUser(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery().in(SysRole::getId, roleIds)
                .list()
                .stream()
                .map(SysRole::getRoleKey)
                .filter(StringUtils::hasText)
                .toList();
    }

    private LambdaQueryWrapper<SysRole> buildWrapper(RoleQuery query) {
        return new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(query.getRoleName()), SysRole::getRoleName, query.getRoleName())
                .like(StringUtils.hasText(query.getRoleKey()), SysRole::getRoleKey, query.getRoleKey())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByAsc(SysRole::getRoleSort);
    }

    private RoleVO toVO(SysRole role) {
        return RoleVO.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleKey(role.getRoleKey())
                .roleSort(role.getRoleSort())
                .dataScope(role.getDataScope())
                .status(role.getStatus())
                .createTime(role.getCreateTime())
                .remark(role.getRemark())
                .build();
    }

    private void checkRoleKeyUnique(String roleKey, Long excludeId) {
        if (!StringUtils.hasText(roleKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色权限标识不能为空");
        }
        Long count = lambdaQuery()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeId != null, SysRole::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色权限标识已存在：" + roleKey);
        }
    }
}

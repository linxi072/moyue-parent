package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.UserQuery;
import com.moyue.system.domain.entity.SysDept;
import com.moyue.system.domain.entity.SysMenu;
import com.moyue.system.domain.entity.SysRole;
import com.moyue.system.domain.entity.SysRoleMenu;
import com.moyue.system.domain.entity.SysUser;
import com.moyue.system.domain.entity.SysUserRole;
import com.moyue.system.domain.vo.UserProfileVO;
import com.moyue.system.domain.vo.UserVO;
import com.moyue.system.mapper.SysDeptMapper;
import com.moyue.system.mapper.SysMenuMapper;
import com.moyue.system.mapper.SysRoleMapper;
import com.moyue.system.mapper.SysRoleMenuMapper;
import com.moyue.system.mapper.SysUserMapper;
import com.moyue.system.mapper.SysUserRoleMapper;
import com.moyue.system.service.SysUserService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理域（①）实现。
 *
 * <p>要点：<ul>
 *   <li>密码一律 BCrypt 落库，编辑时「密码为空则不改」，避免误清空；</li>
 *   <li>角色分配采用先清后插，单事务内完成，避免中间态；</li>
 *   <li>删除为逻辑删除（is_deleted），同时物理清理 sys_user_role 关联。</li>
 * </ul>
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> pageUsers(UserQuery query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .eq(query.getUserType() != null, SysUser::getUserType, query.getUserType())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(SysUser::getUsername, query.getKeyword())
                        .or().like(SysUser::getNickname, query.getKeyword())
                        .or().like(SysUser::getPhone, query.getKeyword()))
                .ge(StringUtils.hasText(query.getBeginTime()), SysUser::getCreateTime, query.getBeginTime())
                .le(StringUtils.hasText(query.getEndTime()), SysUser::getCreateTime, query.getEndTime())
                .orderByDesc(SysUser::getId);

        var page = PageUtils.<SysUser>page(query);
        var result = page(page, wrapper);

        // 批量补部门名，避免逐行查库（N+1）
        Map<Long, String> deptNames = result.getRecords().stream()
                .map(SysUser::getDeptId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(id -> id,
                        id -> {
                            SysDept dept = deptMapper.selectById(id);
                            return dept == null ? "" : dept.getDeptName();
                        }, (a, b) -> a));

        return PageUtils.toResult(result, u -> toVO(u, deptNames));
    }

    @Override
    public UserVO getUserById(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw BusinessException.notFound("用户");
        }
        SysDept dept = user.getDeptId() == null ? null : deptMapper.selectById(user.getDeptId());
        UserVO vo = toVO(user, dept == null ? Map.of() : Map.of(dept.getId(), dept.getDeptName()));
        vo.setRoleIds(listRoleIdsByUser(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(SysUser entity, List<Long> roleIds) {
        checkUsernameUnique(entity.getUsername(), null);
        checkPhoneUnique(entity.getPhone(), null);
        if (StringUtils.hasText(entity.getPassword())) {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        if (entity.getUserType() == null) {
            entity.setUserType(Constants.USER_TYPE_OPERATOR);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(Constants.STATUS_ENABLE);
        }
        save(entity);
        assignRoles(entity.getId(), roleIds);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(SysUser entity, List<Long> roleIds) {
        SysUser exist = getById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("用户");
        }
        checkUsernameUnique(entity.getUsername(), entity.getId());
        checkPhoneUnique(entity.getPhone(), entity.getId());
        // 密码为空表示不改
        if (!StringUtils.hasText(entity.getPassword())) {
            entity.setPassword(null);
        } else {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        boolean ok = updateById(entity);
        if (roleIds != null) {
            assignRoles(entity.getId(), roleIds);
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        SysUser exist = getById(id);
        if (exist == null) {
            throw BusinessException.notFound("用户");
        }
        if (id != null && id == 1L) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置超管账号不可删除");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        return removeById(id);
    }

    @Override
    public List<Long> listRoleIdsByUser(Long userId) {
        List<SysUserRole> list = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(SysUserRole::getRoleId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return true;
        }
        List<SysUserRole> rows = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            SysUserRole row = new SysUserRole();
            row.setUserId(userId);
            row.setRoleId(roleId);
            rows.add(row);
        }
        for (SysUserRole row : rows) {
            userRoleMapper.insert(row);
        }
        return true;
    }

    @Override
    public boolean changeStatus(Long userId, Integer status) {
        SysUser exist = getById(userId);
        if (exist == null) {
            throw BusinessException.notFound("用户");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        return updateById(update);
    }

    @Override
    public boolean resetPassword(Long userId, String password) {
        SysUser exist = getById(userId);
        if (exist == null) {
            throw BusinessException.notFound("用户");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(password));
        return updateById(update);
    }

    private UserVO toVO(SysUser user, Map<Long, String> deptNames) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .deptId(user.getDeptId())
                .deptName(user.getDeptId() == null ? null : deptNames.get(user.getDeptId()))
                .userType(user.getUserType())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .loginIp(user.getLoginIp())
                .loginDate(user.getLoginDate())
                .createTime(user.getCreateTime())
                .remark(user.getRemark())
                .build();
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        Long count = lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "登录账号已存在：" + username);
        }
    }

    private void checkPhoneUnique(String phone, Long excludeId) {
        if (!StringUtils.hasText(phone)) {
            return;
        }
        Long count = lambdaQuery()
                .eq(SysUser::getPhone, phone)
                .ne(excludeId != null, SysUser::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号已被占用");
        }
    }

    // ------------------------------------------------------------------
    // 个人中心（【架构缺口 G-11】补齐）
    // ------------------------------------------------------------------

    @Override
    public UserProfileVO getProfile(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        UserVO vo = getUserById(userId);
        UserProfileVO profile = new UserProfileVO();
        BeanUtils.copyProperties(vo, profile);

        List<Long> roleIds = listRoleIdsByUser(userId);
        profile.setRoleKeys(roleIds.isEmpty() ? List.of()
                : roleMapper.selectBatchIds(roleIds).stream()
                        .map(SysRole::getRoleKey)
                        .filter(StringUtils::hasText)
                        .toList());
        profile.setPermissions(permissionsOf(roleIds));
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProfile(Long userId, SysUser patch) {
        SysUser exist = getById(userId);
        if (exist == null) {
            throw BusinessException.notFound("用户");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        // 白名单：只放行这几个字段，其余（status / userType / password / deptId）一律忽略
        update.setNickname(patch.getNickname());
        update.setPhone(patch.getPhone());
        update.setEmail(patch.getEmail());
        update.setAvatar(patch.getAvatar());
        update.setRemark(patch.getRemark());
        if (StringUtils.hasText(patch.getPhone())) {
            checkPhoneUnique(patch.getPhone(), userId);
        }
        return updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "原密码与新密码均不能为空");
        }
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码长度需为 8 ~ 20 位");
        }
        SysUser exist = getById(userId);
        if (exist == null) {
            throw BusinessException.notFound("用户");
        }
        if (!passwordEncoder.matches(oldPassword, exist.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        return updateById(update);
    }

    /**
     * 汇总角色下的按钮权限串；命中超管角色（{@code admin}）时返回通配 {@code *:*:*}。
     */
    private List<String> permissionsOf(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<String> keys = roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleKey)
                .filter(StringUtils::hasText)
                .toList();
        if (keys.contains(Constants.SUPER_ROLE_KEY)) {
            return List.of(Constants.ALL_PERMISSION);
        }
        List<Long> menuIds = roleMenuMapper
                .selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectBatchIds(menuIds).stream()
                .filter(m -> Constants.MENU_TYPE_BUTTON.equals(m.getMenuType()))
                .map(SysMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}

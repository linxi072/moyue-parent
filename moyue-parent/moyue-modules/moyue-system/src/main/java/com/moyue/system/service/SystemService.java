package com.moyue.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.JwtProvider;
import com.moyue.common.ResultCode;
import com.moyue.system.entity.SysDeptEntity;
import com.moyue.system.entity.SysMenuEntity;
import com.moyue.system.entity.SysRoleEntity;
import com.moyue.system.entity.SysRoleMenuEntity;
import com.moyue.system.entity.SysUserEntity;
import com.moyue.system.entity.SysUserRoleEntity;
import com.moyue.system.mapper.SysDeptMapper;
import com.moyue.system.mapper.SysMenuMapper;
import com.moyue.system.mapper.SysRoleMapper;
import com.moyue.system.mapper.SysRoleMenuMapper;
import com.moyue.system.mapper.SysUserMapper;
import com.moyue.system.mapper.SysUserRoleMapper;
import com.moyue.system.vo.AdminLoginVO;
import com.moyue.system.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统管理（RBAC 运营后台）业务：部门 / 用户 / 角色 / 菜单 的增删改查、树形构建、
 * 用户-角色 / 角色-菜单绑定，以及后台登录与当前管理员菜单树。
 * <p>所有写操作经 AdminRoleInterceptor（moyue-common）在 /api/v1/admin/** 上做 role=3 断言；
 * 后台登录走白名单 /api/v1/system/login，签发 role=3 令牌。</p>
 */
@Service
public class SystemService {

    /** 管理员角色值，对齐 user.role 枚举：1 读者 / 2 作者 / 3 管理员 */
    private static final int ADMIN_ROLE = 3;

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysMenuMapper menuMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private JwtProvider jwtProvider;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${moyue.jwt.access-ttl:7200000}")
    private long accessTtl;

    // ====================== 部门 ======================

    /** 部门树（顶级 + 嵌套 children），按 order_num、id 升序 */
    public List<SysDeptEntity> listDepts() {
        List<SysDeptEntity> all = deptMapper.selectList(Wrappers.<SysDeptEntity>lambdaQuery());
        return buildDeptTree(all);
    }

    public SysDeptEntity getDept(Long id) {
        SysDeptEntity e = deptMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return e;
    }

    @Transactional
    public SysDeptEntity createDept(Long parentId, String deptName, Integer orderNum,
                                     String leader, String phone, String email, Integer status) {
        if (deptName == null || deptName.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "部门名称不能为空");
        }
        if (parentId != null && parentId != 0 && deptMapper.selectById(parentId) == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "父部门不存在");
        }
        SysDeptEntity e = new SysDeptEntity();
        e.setParentId(parentId == null ? 0L : parentId);
        e.setDeptName(deptName.trim());
        e.setOrderNum(orderNum == null ? 0 : orderNum);
        e.setLeader(leader);
        e.setPhone(phone);
        e.setEmail(email);
        e.setStatus(status == null ? 1 : status);
        e.setIsDeleted(0);
        deptMapper.insert(e);
        return e;
    }

    @Transactional
    public SysDeptEntity updateDept(Long id, Long parentId, String deptName, Integer orderNum,
                                    String leader, String phone, String email, Integer status) {
        SysDeptEntity e = getDept(id);
        if (parentId != null) {
            if (parentId == id) {
                throw new BizException(ResultCode.PARAM_ERROR, "父部门不能是自己");
            }
            if (parentId != 0 && deptMapper.selectById(parentId) == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "父部门不存在");
            }
            e.setParentId(parentId);
        }
        if (deptName != null) {
            if (deptName.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "部门名称不能为空");
            }
            e.setDeptName(deptName.trim());
        }
        if (orderNum != null) e.setOrderNum(orderNum);
        if (leader != null) e.setLeader(leader);
        if (phone != null) e.setPhone(phone);
        if (email != null) e.setEmail(email);
        if (status != null) e.setStatus(status);
        deptMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteDept(Long id) {
        getDept(id);
        long children = deptMapper.selectCount(
                Wrappers.<SysDeptEntity>lambdaQuery().eq(SysDeptEntity::getParentId, id));
        if (children > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "存在子部门，无法删除");
        }
        long users = userMapper.selectCount(
                Wrappers.<SysUserEntity>lambdaQuery().eq(SysUserEntity::getDeptId, id));
        if (users > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "部门下存在用户，无法删除");
        }
        deptMapper.deleteById(id);
    }

    // ====================== 菜单 ======================

    /** 菜单树（目录 / 菜单 / 按钮嵌套），按 order_num、id 升序 */
    public List<SysMenuEntity> listMenus() {
        List<SysMenuEntity> all = menuMapper.selectList(Wrappers.<SysMenuEntity>lambdaQuery());
        return buildMenuTree(all);
    }

    public SysMenuEntity getMenu(Long id) {
        SysMenuEntity e = menuMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return e;
    }

    @Transactional
    public SysMenuEntity createMenu(Long parentId, String menuName, Integer menuType, String path,
                                    String component, String perms, String icon, Integer orderNum, Integer status) {
        if (menuName == null || menuName.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "菜单名称不能为空");
        }
        if (parentId != null && parentId != 0 && menuMapper.selectById(parentId) == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "父菜单不存在");
        }
        SysMenuEntity e = new SysMenuEntity();
        e.setParentId(parentId == null ? 0L : parentId);
        e.setMenuName(menuName.trim());
        e.setMenuType(menuType == null ? 1 : menuType);
        e.setPath(path);
        e.setComponent(component);
        e.setPerms(perms);
        e.setIcon(icon);
        e.setOrderNum(orderNum == null ? 0 : orderNum);
        e.setStatus(status == null ? 1 : status);
        e.setIsDeleted(0);
        menuMapper.insert(e);
        return e;
    }

    @Transactional
    public SysMenuEntity updateMenu(Long id, Long parentId, String menuName, Integer menuType, String path,
                                    String component, String perms, String icon, Integer orderNum, Integer status) {
        SysMenuEntity e = getMenu(id);
        if (parentId != null) {
            if (parentId == id) {
                throw new BizException(ResultCode.PARAM_ERROR, "父菜单不能是自己");
            }
            if (parentId != 0 && menuMapper.selectById(parentId) == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "父菜单不存在");
            }
            e.setParentId(parentId);
        }
        if (menuName != null) {
            if (menuName.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "菜单名称不能为空");
            }
            e.setMenuName(menuName.trim());
        }
        if (menuType != null) e.setMenuType(menuType);
        if (path != null) e.setPath(path);
        if (component != null) e.setComponent(component);
        if (perms != null) e.setPerms(perms);
        if (icon != null) e.setIcon(icon);
        if (orderNum != null) e.setOrderNum(orderNum);
        if (status != null) e.setStatus(status);
        menuMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteMenu(Long id) {
        getMenu(id);
        long children = menuMapper.selectCount(
                Wrappers.<SysMenuEntity>lambdaQuery().eq(SysMenuEntity::getParentId, id));
        if (children > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "存在子菜单，无法删除");
        }
        menuMapper.deleteById(id);
        // 清理角色-菜单关联
        roleMenuMapper.delete(Wrappers.<SysRoleMenuEntity>lambdaQuery().eq(SysRoleMenuEntity::getMenuId, id));
    }

    /** 角色集合对应的全部菜单 ID（去重） */
    public List<Long> listMenuIdsByRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysRoleMenuEntity> rows = roleMenuMapper.selectList(
                Wrappers.<SysRoleMenuEntity>lambdaQuery().in(SysRoleMenuEntity::getRoleId, roleIds));
        return rows.stream().map(SysRoleMenuEntity::getMenuId).distinct().collect(Collectors.toList());
    }

    // ====================== 角色 ======================

    /** 角色列表（可选 status 过滤），按 id 升序 */
    public List<SysRoleEntity> listRoles(Integer status) {
        return roleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(status != null, SysRoleEntity::getStatus, status)
                .orderByAsc(SysRoleEntity::getId));
    }

    public SysRoleEntity getRole(Long id) {
        SysRoleEntity e = roleMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return e;
    }

    @Transactional
    public SysRoleEntity createRole(String roleName, String roleKey, Integer dataScope,
                                    String deptIds, Integer status) {
        if (roleName == null || roleName.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色名称不能为空");
        }
        if (roleKey == null || roleKey.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色标识不能为空");
        }
        if (roleMapper.selectCount(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getRoleKey, roleKey.trim())) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "角色标识已存在");
        }
        SysRoleEntity e = new SysRoleEntity();
        e.setRoleName(roleName.trim());
        e.setRoleKey(roleKey.trim());
        e.setDataScope(dataScope == null ? 1 : dataScope);
        e.setDeptIds(deptIds);
        e.setStatus(status == null ? 1 : status);
        e.setIsDeleted(0);
        roleMapper.insert(e);
        return e;
    }

    @Transactional
    public SysRoleEntity updateRole(Long id, String roleName, String roleKey, Integer dataScope,
                                    String deptIds, Integer status) {
        SysRoleEntity e = getRole(id);
        if (roleName != null) {
            if (roleName.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "角色名称不能为空");
            }
            e.setRoleName(roleName.trim());
        }
        if (roleKey != null) {
            if (roleKey.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "角色标识不能为空");
            }
            if (!roleKey.trim().equals(e.getRoleKey())
                    && roleMapper.selectCount(Wrappers.<SysRoleEntity>lambdaQuery()
                    .eq(SysRoleEntity::getRoleKey, roleKey.trim())
                    .ne(SysRoleEntity::getId, id)) > 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "角色标识已存在");
            }
            e.setRoleKey(roleKey.trim());
        }
        if (dataScope != null) e.setDataScope(dataScope);
        if (deptIds != null) e.setDeptIds(deptIds);
        if (status != null) e.setStatus(status);
        roleMapper.updateById(e);
        return e;
    }

    @Transactional
    public void deleteRole(Long id) {
        getRole(id);
        long boundUsers = userRoleMapper.selectCount(
                Wrappers.<SysUserRoleEntity>lambdaQuery().eq(SysUserRoleEntity::getRoleId, id));
        if (boundUsers > 0) {
            throw new BizException(ResultCode.FORBIDDEN, "角色已分配给用户，无法删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenuEntity>lambdaQuery().eq(SysRoleMenuEntity::getRoleId, id));
    }

    /** 设置角色菜单（先清后插） */
    @Transactional
    public void setRoleMenus(Long roleId, List<Long> menuIds) {
        getRole(roleId);
        roleMenuMapper.delete(Wrappers.<SysRoleMenuEntity>lambdaQuery().eq(SysRoleMenuEntity::getRoleId, roleId));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                SysRoleMenuEntity r = new SysRoleMenuEntity();
                r.setRoleId(roleId);
                r.setMenuId(menuId);
                roleMenuMapper.insert(r);
            }
        }
    }

    /** 角色已授权菜单 ID 列表 */
    public List<Long> getRoleMenuIds(Long roleId) {
        List<SysRoleMenuEntity> rows = roleMenuMapper.selectList(
                Wrappers.<SysRoleMenuEntity>lambdaQuery().eq(SysRoleMenuEntity::getRoleId, roleId));
        return rows.stream().map(SysRoleMenuEntity::getMenuId).collect(Collectors.toList());
    }

    // ====================== 用户 ======================

    /** 用户分页（可选部门 / 关键字过滤），响应 UserVO（屏蔽密码、填充部门名与角色） */
    public PageResult<UserVO> listUsers(int page, int size, Long deptId, String keyword) {
        Page<SysUserEntity> param = new Page<>(page, size);
        LambdaQueryWrapper<SysUserEntity> q = Wrappers.<SysUserEntity>lambdaQuery();
        if (deptId != null) {
            q.eq(SysUserEntity::getDeptId, deptId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(SysUserEntity::getUsername, k).or().like(SysUserEntity::getNickname, k));
        }
        q.orderByDesc(SysUserEntity::getCreateTime);
        IPage<SysUserEntity> result = userMapper.selectPage(param, q);

        List<SysDeptEntity> depts = deptMapper.selectList(Wrappers.<SysDeptEntity>lambdaQuery());
        Map<Long, String> deptNameMap = depts.stream()
                .collect(Collectors.toMap(SysDeptEntity::getId, SysDeptEntity::getDeptName, (a, b) -> a));
        Map<Long, List<Long>> userRoleMap = loadUserRoleMap(
                result.getRecords().stream().map(SysUserEntity::getId).collect(Collectors.toList()));

        List<UserVO> vos = result.getRecords().stream()
                .map(u -> toUserVO(u, deptNameMap, userRoleMap))
                .collect(Collectors.toList());

        PageResult<UserVO> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setPage(page);
        pr.setSize(size);
        pr.setRecords(vos);
        return pr;
    }

    public UserVO getUser(Long id) {
        SysUserEntity u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        Map<Long, String> deptNameMap = new HashMap<>();
        if (u.getDeptId() != null && u.getDeptId() != 0) {
            SysDeptEntity d = deptMapper.selectById(u.getDeptId());
            if (d != null) deptNameMap.put(d.getId(), d.getDeptName());
        }
        Map<Long, List<Long>> userRoleMap = loadUserRoleMap(List.of(id));
        return toUserVO(u, deptNameMap, userRoleMap);
    }

    @Transactional
    public UserVO createUser(Long deptId, String username, String nickname, String password,
                             String email, String phone, Integer status, List<Long> roleIds) {
        if (username == null || username.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "登录账号不能为空");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "昵称不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "密码不能为空");
        }
        if (userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, username.trim())) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "登录账号已存在");
        }
        SysUserEntity u = new SysUserEntity();
        u.setDeptId(deptId == null ? 0L : deptId);
        u.setUsername(username.trim());
        u.setNickname(nickname.trim());
        u.setPassword(encoder.encode(password));
        u.setEmail(email);
        u.setPhone(phone);
        u.setStatus(status == null ? 1 : status);
        u.setIsDeleted(0);
        userMapper.insert(u);
        bindUserRoles(u.getId(), roleIds);
        return getUser(u.getId());
    }

    @Transactional
    public UserVO updateUser(Long id, Long deptId, String username, String nickname, String password,
                             String email, String phone, Integer status, List<Long> roleIds) {
        SysUserEntity u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (username != null) {
            if (username.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "登录账号不能为空");
            }
            if (!username.trim().equals(u.getUsername())
                    && userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                    .eq(SysUserEntity::getUsername, username.trim())
                    .ne(SysUserEntity::getId, id)) > 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "登录账号已存在");
            }
            u.setUsername(username.trim());
        }
        if (nickname != null) {
            if (nickname.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "昵称不能为空");
            }
            u.setNickname(nickname.trim());
        }
        if (deptId != null) u.setDeptId(deptId);
        if (password != null && !password.isBlank()) u.setPassword(encoder.encode(password));
        if (email != null) u.setEmail(email);
        if (phone != null) u.setPhone(phone);
        if (status != null) u.setStatus(status);
        userMapper.updateById(u);
        if (roleIds != null) {
            bindUserRoles(id, roleIds);
        }
        return getUser(id);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<SysUserRoleEntity>lambdaQuery().eq(SysUserRoleEntity::getUserId, id));
    }

    /** 设置用户角色（先清后插） */
    @Transactional
    public void setUserRoles(Long userId, List<Long> roleIds) {
        if (userMapper.selectById(userId) == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        bindUserRoles(userId, roleIds);
    }

    /** 用户已分配角色 ID 列表 */
    public List<Long> getUserRoleIds(Long userId) {
        List<SysUserRoleEntity> rows = userRoleMapper.selectList(
                Wrappers.<SysUserRoleEntity>lambdaQuery().eq(SysUserRoleEntity::getUserId, userId));
        return rows.stream().map(SysUserRoleEntity::getRoleId).collect(Collectors.toList());
    }

    // ====================== 鉴权 ======================

    /** 后台登录：校验 sys_user + BCrypt，签发 role=3 令牌 */
    public AdminLoginVO login(String username, String password) {
        SysUserEntity u = userMapper.selectOne(
                Wrappers.<SysUserEntity>lambdaQuery().eq(SysUserEntity::getUsername, username));
        if (u == null || !encoder.matches(password, u.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "账号或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        String token = jwtProvider.createAccessToken(u.getId(), ADMIN_ROLE, accessTtl);
        AdminLoginVO vo = new AdminLoginVO();
        vo.setAccessToken(token);
        vo.setUserId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setRole(ADMIN_ROLE);
        return vo;
    }

    /** 当前管理员菜单树：由 X-User-Id（sys_user.id）→ 角色 → 菜单 */
    public List<SysMenuEntity> getCurrentMenus(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<Long> roleIds = getUserRoleIds(userId);
        List<Long> menuIds = listMenuIdsByRoles(roleIds);
        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysMenuEntity> menus = menuMapper.selectBatchIds(menuIds);
        return buildMenuTree(menus);
    }

    // ====================== 工具 ======================

    private void bindUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRoleEntity>lambdaQuery().eq(SysUserRoleEntity::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRoleEntity r = new SysUserRoleEntity();
                r.setUserId(userId);
                r.setRoleId(roleId);
                userRoleMapper.insert(r);
            }
        }
    }

    private Map<Long, List<Long>> loadUserRoleMap(List<Long> userIds) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        List<SysUserRoleEntity> rows = userRoleMapper.selectList(
                Wrappers.<SysUserRoleEntity>lambdaQuery().in(SysUserRoleEntity::getUserId, userIds));
        for (SysUserRoleEntity r : rows) {
            map.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r.getRoleId());
        }
        return map;
    }

    private UserVO toUserVO(SysUserEntity u, Map<Long, String> deptNameMap, Map<Long, List<Long>> userRoleMap) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setDeptId(u.getDeptId());
        vo.setDeptName(u.getDeptId() != null && u.getDeptId() != 0 ? deptNameMap.get(u.getDeptId()) : null);
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setEmail(u.getEmail());
        vo.setPhone(u.getPhone());
        vo.setStatus(u.getStatus());
        vo.setRoleIds(userRoleMap.getOrDefault(u.getId(), new ArrayList<>()));
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }

    /**
     * 部门树构建：按 parent_id 归并 children，顶层 parent_id=0 或父不存在（孤儿归顶）；
     * 同级按 order_num、id 升序。
     */
    private List<SysDeptEntity> buildDeptTree(List<SysDeptEntity> all) {
        List<SysDeptEntity> roots = new ArrayList<>();
        Map<Long, SysDeptEntity> map = new HashMap<>();
        for (SysDeptEntity e : all) {
            map.put(e.getId(), e);
        }
        for (SysDeptEntity e : all) {
            Long pid = e.getParentId();
            if (pid == null || pid == 0) {
                roots.add(e);
            } else {
                SysDeptEntity parent = map.get(pid);
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(e);
                } else {
                    roots.add(e);
                }
            }
        }
        sortDept(roots);
        return roots;
    }

    private void sortDept(List<SysDeptEntity> nodes) {
        if (nodes == null) return;
        nodes.sort(Comparator.comparing(SysDeptEntity::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SysDeptEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (SysDeptEntity n : nodes) {
            sortDept(n.getChildren());
        }
    }

    /**
     * 菜单树构建：逻辑同部门树（目录 / 菜单 / 按钮嵌套）。
     */
    private List<SysMenuEntity> buildMenuTree(List<SysMenuEntity> all) {
        List<SysMenuEntity> roots = new ArrayList<>();
        Map<Long, SysMenuEntity> map = new HashMap<>();
        for (SysMenuEntity e : all) {
            map.put(e.getId(), e);
        }
        for (SysMenuEntity e : all) {
            Long pid = e.getParentId();
            if (pid == null || pid == 0) {
                roots.add(e);
            } else {
                SysMenuEntity parent = map.get(pid);
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(e);
                } else {
                    roots.add(e);
                }
            }
        }
        sortMenu(roots);
        return roots;
    }

    private void sortMenu(List<SysMenuEntity> nodes) {
        if (nodes == null) return;
        nodes.sort(Comparator.comparing(SysMenuEntity::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SysMenuEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (SysMenuEntity n : nodes) {
            sortMenu(n.getChildren());
        }
    }
}

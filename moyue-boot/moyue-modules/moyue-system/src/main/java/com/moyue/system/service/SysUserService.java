package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.UserQuery;
import com.moyue.system.domain.entity.SysUser;
import com.moyue.system.domain.vo.UserProfileVO;
import com.moyue.system.domain.vo.UserVO;

import java.util.List;

/**
 * 用户管理域（①）服务。
 *
 * @author moyue
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 用户分页（按部门 / 关键字 / 主体类型 / 状态过滤）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<UserVO> pageUsers(UserQuery query);

    /**
     * 用户详情（含部门名与已分配角色）。
     *
     * @param id 用户 ID
     * @return 用户视图
     */
    UserVO getUserById(Long id);

    /**
     * 新增用户（密码 BCrypt 加密）。
     *
     * @param entity 用户实体
     * @param roleIds 待分配角色 ID，可为空
     * @return 用户 ID
     */
    Long createUser(SysUser entity, List<Long> roleIds);

    /**
     * 编辑用户（密码为空则不修改）。
     *
     * @param entity 用户实体
     * @param roleIds 待分配角色 ID，为空表示不调整角色
     * @return 是否成功
     */
    boolean updateUser(SysUser entity, List<Long> roleIds);

    /**
     * 删除用户（逻辑删除 + 清理角色关联）。
     *
     * @param id 用户 ID
     * @return 是否成功
     */
    boolean deleteUser(Long id);

    /**
     * 查询用户已分配角色 ID。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> listRoleIdsByUser(Long userId);

    /**
     * 设置用户角色（先清后插）。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     * @return 是否成功
     */
    boolean assignRoles(Long userId, List<Long> roleIds);

    /**
     * 启停用户。
     *
     * @param userId 用户 ID
     * @param status 0 禁用 / 1 正常
     * @return 是否成功
     */
    boolean changeStatus(Long userId, Integer status);

    /**
     * 重置密码。
     *
     * @param userId   用户 ID
     * @param password 明文密码
     * @return 是否成功
     */
    boolean resetPassword(Long userId, String password);

    /**
     * 当前登录者资料（含角色标识与权限串）。
     *
     * @param userId 用户 ID
     * @return 个人中心视图
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 修改个人资料。只允许改昵称 / 手机号 / 邮箱 / 头像 / 备注，
     * 其余字段（状态、主体类型、角色）忽略，防止越权提权。
     *
     * @param userId 用户 ID
     * @param patch  待更新字段
     * @return 是否成功
     */
    boolean updateProfile(Long userId, SysUser patch);

    /**
     * 修改密码（需校验原密码）。
     *
     * @param userId      用户 ID
     * @param oldPassword 原明文密码
     * @param newPassword 新明文密码
     * @return 是否成功
     */
    boolean changePassword(Long userId, String oldPassword, String newPassword);
}

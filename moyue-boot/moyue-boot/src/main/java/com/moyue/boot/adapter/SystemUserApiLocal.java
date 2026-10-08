package com.moyue.boot.adapter;

import com.moyue.common.core.result.R;
import com.moyue.common.security.model.LoginUser;
import com.moyue.common.security.context.UserContext;
import com.moyue.system.api.SystemUserApi;
import com.moyue.system.api.dto.UserDTO;
import com.moyue.system.domain.entity.SysUser;
import com.moyue.system.service.SysRoleService;
import com.moyue.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户域本地实现 —— <b>双项目适配点 ①（跨模块调用）</b>的 boot 形态。
 *
 * <p>cloud 形态下 {@link SystemUserApi} 由 Feign 走 HTTP；单体形态下直接在进程内
 * 调 Service，省掉一次序列化与网络往返。调用方代码不变，这正是把契约定义在
 * api 模块的价值。
 *
 * @author moyue
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "moyue", name = "mode", havingValue = "boot")
@RequiredArgsConstructor
public class SystemUserApiLocal implements SystemUserApi {

    private final SysUserService userService;
    private final SysRoleService roleService;

    @Override
    public R<UserDTO> getUser(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            return R.fail(com.moyue.common.core.exception.ErrorCode.NOT_FOUND.getCode(), "用户不存在");
        }
        return R.ok(toDto(user));
    }

    @Override
    public R<List<UserDTO>> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.ok(List.of());
        }
        List<UserDTO> list = new ArrayList<>();
        for (Long id : ids) {
            SysUser user = userService.getById(id);
            if (user != null) {
                list.add(toDto(user));
            }
        }
        return R.ok(list);
    }

    private UserDTO toDto(SysUser user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setUserType(user.getUserType());
        dto.setPhone(user.getPhone());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        dto.setDeptId(user.getDeptId());
        dto.setLoginDate(user.getLoginDate());
        try {
            dto.setRoleKeys(roleService.listRoleKeysByUser(user.getId()));
        } catch (Exception e) {
            log.warn("角色查询失败，返回空角色：userId={}", user.getId());
            dto.setRoleKeys(List.of());
        }
        return dto;
    }
}

package com.moyue.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.user.entity.UserEntity;
import com.moyue.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户业务：资料查询、分页与资料更新。
 */
@Service
public class UserService {

    private static final int ROLE_ADMIN = 3;

    @Autowired
    private UserMapper userMapper;

    /** 按 ID 查询用户资料 */
    public UserEntity getUserById(Long id) {
        return userMapper.selectById(id);
    }

    /** 用户分页（按创建时间倒序） */
    public PageResult<UserEntity> pageUsers(int page, int size) {
        Page<UserEntity> p = new Page<>(page, size);
        userMapper.selectPage(p, null);

        PageResult<UserEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /**
     * 更新资料（昵称 / 头像）：仅本人或管理员。
     * 说明：手机号、角色、状态不在本接口范围内（属后台管理动作），避免越权改角色。
     */
    @Transactional
    public UserEntity updateProfile(Long id, long currentUserId, int role, String nickname, String avatarUrl) {
        UserEntity e = userMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (role != ROLE_ADMIN && !Objects.equals(id, currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (nickname != null) {
            if (nickname.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "昵称不能为空");
            }
            e.setNickname(nickname.trim());
        }
        if (avatarUrl != null) {
            e.setAvatarUrl(avatarUrl);
        }
        userMapper.updateById(e);
        return e;
    }
}

package com.moyue.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.user.entity.UserEntity;
import com.moyue.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户业务：资料查询与分页。
 */
@Service
public class UserService {

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
}

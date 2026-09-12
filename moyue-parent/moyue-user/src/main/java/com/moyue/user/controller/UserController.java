package com.moyue.user.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.user.entity.UserEntity;
import com.moyue.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：资料查询 / 分页。
 * 路径前缀 /api/v1 与网关路由、Feign UserClient 保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired
    private UserService userService;

    /** 按 ID 获取用户资料（供 Feign UserClient 调用，路径必须为 /api/v1/users/{id}） */
    @GetMapping("/users/{id}")
    public R<UserEntity> getUser(@PathVariable Long id) {
        UserEntity user = userService.getUserById(id);
        if (user == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return R.ok(user);
    }

    /** 用户分页（可选 page / size，默认第 1 页、每页 20 条） */
    @GetMapping("/users")
    public R<PageResult<UserEntity>> listUsers(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return R.ok(userService.pageUsers(page, size));
    }
}

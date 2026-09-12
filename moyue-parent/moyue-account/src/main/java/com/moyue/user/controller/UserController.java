package com.moyue.user.controller;

import com.moyue.api.dto.PageResult;
import com.moyue.api.dto.UserDTO;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.user.entity.UserEntity;
import com.moyue.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：资料查询 / 分页 / 资料更新。
 * 路径前缀 /api/v1 与网关路由、Feign UserClient 保持一致。
 * 资料更新仅限本人或管理员（身份取网关注入的 X-User-Id / X-User-Role）。
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 按 ID 获取用户资料（供 Feign UserClient 调用，路径必须为 /api/v1/users/{id}）。
     * 返回 UserDTO 与 UserClient 声明的 R&lt;UserDTO&gt; 严格对齐，避免依赖 Jackson 忽略未知字段的隐式行为。
     */
    @GetMapping("/users/{id}")
    public R<UserDTO> getUser(@PathVariable Long id) {
        UserEntity user = userService.getUserById(id);
        if (user == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return R.ok(UserService.toDto(user));
    }

    /** 用户分页（可选 page / size，默认第 1 页、每页 20 条） */
    @GetMapping("/users")
    public R<PageResult<UserDTO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(UserService.toDtoPage(userService.pageUsers(page, size)));
    }

    /** 更新资料（昵称 / 头像）：本人或管理员 */
    @PutMapping("/users/{id}")
    public R<UserDTO> updateProfile(@PathVariable Long id,
                                    @RequestBody UpdateProfileRequest req,
                                    HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(UserService.toDto(
                userService.updateProfile(id, userId, role, req.getNickname(), req.getAvatarUrl())));
    }

    // ------------------------------ 上下文工具 ------------------------------

    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    private int currentRole(HttpServletRequest request) {
        String role = request.getHeader(Constants.USER_ROLE_HEADER);
        if (role == null || role.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(role.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------ 请求体 ------------------------------

    /** 更新资料请求（字段均可选，仅更新非空字段） */
    @Data
    public static class UpdateProfileRequest {
        private String nickname;
        private String avatarUrl;
    }
}

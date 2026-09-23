package com.moyue.api.account.client;

import com.moyue.api.account.dto.UserDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.user.service.UserService;
import org.springframework.stereotype.Component;

/**
 * 用户服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-account) 已移除 OpenFeign，改为直接注入 {@link UserService} 委托调用，
 * 对外契约（方法签名与返回类型）保持不变，调用方无感知。
 */
@Component
public class UserClient {

    private final UserService userService;

    public UserClient(UserService userService) {
        this.userService = userService;
    }

    /** 按 ID 获取用户资料 */
    public R<UserDTO> getUser(Long id) {
        try {
            return R.ok(UserService.toDto(userService.getUserById(id)));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

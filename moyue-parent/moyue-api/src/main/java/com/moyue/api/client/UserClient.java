package com.moyue.api.account.client;

import com.moyue.api.account.dto.UserDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 客户端（moyue-account）。
 * 返回类型必须包裹 R&lt;T&gt;，与控制器实际响应 {@code R<UserDTO>} 的 JSON 结构一致，
 * 否则 Feign 会直接把 R 包装体反序列化成 DTO 而失败。
 * UserController 已改为返回 UserDTO（在 UserService.toDto 做实体转换），与此处声明严格对齐，
 * 不再依赖 Jackson 忽略未知字段的隐式行为；DTO 为对外契约，不含 password 等敏感字段。
 */
@FeignClient(name = "moyue-account", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    /** 按 ID 获取用户资料 */
    @GetMapping("/api/v1/users/{id}")
    R<UserDTO> getUser(@PathVariable("id") Long id);
}

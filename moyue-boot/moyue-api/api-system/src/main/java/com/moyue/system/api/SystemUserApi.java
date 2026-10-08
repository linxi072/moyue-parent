package com.moyue.system.api;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.system.api.dto.UserDTO;
import com.moyue.system.api.fallback.SystemUserApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 用户域跨服务契约。
 *
 * <p><b>双项目适配点 ①（跨模块调用）</b>：<ul>
 *   <li><b>cloud</b>：由 Feign 生成 HTTP 客户端，经网关 / 注册中心调用 moyue-system；</li>
 *   <li><b>boot</b>：单体进程内没有 HTTP 跳转，由 {@code moyue-boot} 模块提供
 *       {@code SystemUserApiLocal} 本地实现（直接注入 Service），Feign 不启用。</li>
 * </ul>
 * 业务侧只依赖本接口，两种形态下代码零差异。
 *
 * @author moyue
 */
@FeignClient(name = "moyue-system",
        path = Constants.ADMIN_PATH_PREFIX + "/system",
        fallback = SystemUserApiFallback.class)
public interface SystemUserApi {

    /**
     * 按 ID 查用户。
     *
     * @param userId 用户 ID
     * @return 用户契约
     */
    @GetMapping("/users/{userId}")
    R<UserDTO> getUser(@PathVariable("userId") Long userId);

    /**
     * 批量查用户（供评论、订单等场景做用户快照补全）。
     *
     * @param ids 用户 ID 列表
     * @return 用户契约列表
     */
    @PostMapping("/users/batch")
    R<List<UserDTO>> listByIds(@RequestBody List<Long> ids);
}

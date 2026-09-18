package com.moyue.api.social.client;

import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 动态服务 Feign 客户端（moyue-social）。
 *
 * <p>publish 对应 social 内部端点 {@code /api/v1/internal/dynamic/publish}（不经网关），
 * 由 {@code ServiceTokenRequestInterceptor} 自动注入 {@code X-Service-Token}，
 * 下游 {@code InternalAuthInterceptor} 校验（P2-I 网关直连防护）。</p>
 *
 * <p>contextId 与同服务其它 Feign 客户端（blogClient / commentClient / imClient / followClient）
 * 区分注册，避免 FeignClientSpecification 同名 bean 冲突。</p>
 */
@FeignClient(name = "moyue-social", contextId = "dynamicClient", fallbackFactory = DynamicClientFallbackFactory.class)
public interface DynamicClient {

    /** 发布动态（内部端点）：落 user_dynamic；幂等由 social 侧 uk_dynamic_ref 保证 */
    @PostMapping("/api/v1/internal/dynamic/publish")
    R<Void> publish(@RequestBody DynamicPublishDTO dto);
}

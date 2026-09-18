package com.moyue.dynamic.controller;

import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.common.R;
import com.moyue.dynamic.service.DynamicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态内部端点（服务间调用，仅供 Feign 使用）：接收 {@code DynamicPublishDTO} 并落库。
 *
 * <p>对应 {@code DynamicClient.publish} 的 {@code /api/v1/internal/dynamic/publish}，不经网关。
 * 路径 {@code /api/v1/internal/**} 由 {@code InternalAuthInterceptor} 校验服务令牌（P2-I），
 * 外部不可达；返回 R.ok 即幂等成功（重复发布由 social 侧 uk_dynamic_ref 兜底）。</p>
 */
@RestController
@RequestMapping("/api/v1/internal")
public class DynamicInternalController {

    @Autowired
    private DynamicService dynamicService;

    /** 发布动态（内部端点）：content 发布/完结、system 打赏后旁路调用 */
    @PostMapping("/dynamic/publish")
    public R<Void> publish(@RequestBody DynamicPublishDTO dto) {
        dynamicService.create(dto);
        return R.ok();
    }
}

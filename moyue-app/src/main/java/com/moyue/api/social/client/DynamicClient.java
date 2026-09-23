package com.moyue.api.social.client;

import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.dynamic.service.DynamicService;
import org.springframework.stereotype.Component;

/**
 * 动态服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-social) 已移除 OpenFeign，改为直接注入 {@link DynamicService} 委托调用。
 */
@Component
public class DynamicClient {

    private final DynamicService dynamicService;

    public DynamicClient(DynamicService dynamicService) {
        this.dynamicService = dynamicService;
    }

    /** 发布动态（幂等）：落 user_dynamic */
    public R<Void> publish(DynamicPublishDTO dto) {
        try {
            dynamicService.create(dto);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

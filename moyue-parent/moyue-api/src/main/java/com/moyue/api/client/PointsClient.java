package com.moyue.api.client;

import com.moyue.api.dto.PointsAccountDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 积分服务 Feign 客户端（moyue-points）。
 * 返回类型包裹 R&lt;T&gt;，与控制器实际响应结构一致。
 */
@FeignClient(name = "moyue-points")
public interface PointsClient {

    /** 查询用户积分账户 */
    @GetMapping("/api/v1/points/accounts/{userId}")
    R<PointsAccountDTO> getAccount(@PathVariable("userId") Long userId);
}

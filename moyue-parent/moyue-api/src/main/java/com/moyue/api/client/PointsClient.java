package com.moyue.api.client;

import com.moyue.api.dto.PointsAccountDTO;
import com.moyue.api.dto.PointsAwardDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 积分服务 Feign 客户端（moyue-commerce）。
 * 返回类型包裹 R&lt;T&gt;，与控制器实际响应结构一致。
 */
@FeignClient(name = "moyue-reader")
public interface PointsClient {

    /** 查询用户积分账户 */
    @GetMapping("/api/v1/points/accounts/{userId}")
    R<PointsAccountDTO> getAccount(@PathVariable("userId") Long userId);

    /**
     * 服务间积分发放（内部端点，不经网关）：阅读时长 / 评论奖励等生产者调用。
     * 返回本次入账后的新余额；调用方必须校验 R.code == 0（业务错误以 HTTP 200 承载）。
     */
    @PostMapping("/api/v1/internal/points/award")
    R<Integer> award(@RequestBody PointsAwardDTO request);
}

package com.moyue.api.social.client;

import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 关注关系 Feign 客户端（moyue-social，契约预留）。
 *
 * <p>供书城详情页「已关注」标识查询；本期仅留契约（{@code isFollowing}），
 * 服务端端点由后续前端需求补齐，故调用方须按 code 显式校验降级（R.code=40002 安全跳过）。</p>
 *
 * <p>contextId 与同服务其它 Feign 客户端区分注册，避免 bean 冲突。</p>
 */
@FeignClient(name = "moyue-social", contextId = "followClient", fallbackFactory = FollowClientFallbackFactory.class)
public interface FollowClient {

    /**
     * 查询粉丝是否关注作者（契约预留，本期服务端未实现 → 降级返回 40002，调用方安全跳过）。
     */
    @GetMapping("/api/v1/follow/is-following")
    R<Boolean> isFollowing(@RequestParam("fanId") Long fanId,
                           @RequestParam("authorId") Long authorId);
}

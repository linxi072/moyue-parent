package com.moyue.api.social.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.follow.service.FollowService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关注关系服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-social) 已移除 OpenFeign，改为直接注入 {@link FollowService} 委托调用。
 *
 * <p>说明：原 Feign 契约 isFollowing 在 social 端为「契约预留」未实现（降级返回 40002）；
 * monolith 内直接复用 {@link FollowService#findFollowedAuthorIds(Long)} 计算「粉丝是否关注作者」，
 * 由进程内真实数据给出确定结果，调用方无需再按 code 做降级跳过。</p>
 */
@Component
public class FollowClient {

    private final FollowService followService;

    public FollowClient(FollowService followService) {
        this.followService = followService;
    }

    /** 查询粉丝是否关注作者 */
    public R<Boolean> isFollowing(Long fanId, Long authorId) {
        try {
            List<Long> followed = followService.findFollowedAuthorIds(fanId);
            return R.ok(followed != null && followed.contains(authorId));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

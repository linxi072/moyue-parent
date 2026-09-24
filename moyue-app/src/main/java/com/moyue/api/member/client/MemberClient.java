package com.moyue.api.member.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.member.service.MemberService;
import org.springframework.stereotype.Component;

/**
 * 会员服务进程内适配器（monolith 版）。
 *
 * <p>原微服务时期的 {@code @FeignClient(moyue-commerce)} 已随拍平移除，改为直接注入
 * {@link MemberService} 委托调用。其余域（如商城结算折扣、阅读免广告）经本客户端消费会员权益，
 * 服务不可用时降级返回 {@link ResultCode#SERVICE_DEGRADED}，不阻断主链路——与
 * {@code PointsClient} / {@code ChapterClient} 同一范式。</p>
 */
@Component
public class MemberClient {

    private final MemberService memberService;

    public MemberClient(MemberService memberService) {
        this.memberService = memberService;
    }

    /** 查询会员权益（免广告 / 折扣率 / 徽章）；不可用时降级 SERVICE_DEGRADED */
    public R<MemberService.MemberBenefits> getBenefits(Long userId) {
        try {
            return R.ok(memberService.getBenefits(userId));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 到期订阅自动降级（供定时任务调用）；返回影响行数，不可用时降级 SERVICE_DEGRADED */
    public R<Integer> syncExpired() {
        try {
            return R.ok(memberService.syncExpired());
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

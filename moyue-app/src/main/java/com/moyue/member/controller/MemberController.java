package com.moyue.member.controller;

import com.moyue.common.R;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.member.annotation.MemberBenefit;
import com.moyue.member.annotation.RequiresMember;
import com.moyue.member.entity.MemberSubscriptionEntity;
import com.moyue.member.entity.MemberTierEntity;
import com.moyue.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 会员订阅接口：套餐列表 / 开通订阅 / 权益查询 / 订阅历史 / 取消。
 * 路径前缀 /api/v1 与网关路由保持一致；userId 口径与 merch/points 一致（调用方显式传入）。
 */
@RestController
@RequestMapping("/api/v1")
public class MemberController {

    @Autowired
    private MemberService memberService;

    /** 在售会员套餐列表 */
    @GetMapping("/member/tiers")
    public R<List<MemberTierEntity>> listTiers() {
        return R.ok(memberService.listOnSaleTiers());
    }

    /** 开通会员订阅（userId + tierCode） */
    @PostMapping("/member/subscribe")
    public R<MemberSubscriptionEntity> subscribe(@RequestBody SubscribeRequest req) {
        return R.ok(memberService.subscribe(req.getUserId(), req.getTierCode()));
    }

    /** 当前会员权益（免广告 / 折扣 / 徽章） */
    @GetMapping("/member/benefits")
    public R<MemberService.MemberBenefits> benefits(@RequestParam Long userId) {
        return R.ok(memberService.getBenefits(userId));
    }

    /** 会员专属空间（需生效中会员） */
    @GetMapping("/member/exclusive")
    @RequiresMember
    public R<MemberExclusiveView> exclusive() {
        Long userId = SecurityContextHolder.currentUserId();
        MemberService.MemberBenefits benefits = memberService.getBenefits(userId);
        MemberExclusiveView view = new MemberExclusiveView();
        view.setWelcome("欢迎来到会员专属空间，尊享免广告 / 折扣 / 专属徽章");
        view.setTierName(benefits.getTierName());
        view.setBenefits(benefits);
        return R.ok(view);
    }

    /** 会员折扣预览（需享「折扣」权益的会员） */
    @GetMapping("/member/exclusive/discount")
    @RequiresMember(benefit = MemberBenefit.DISCOUNT)
    public R<BigDecimal> exclusiveDiscount() {
        Long userId = SecurityContextHolder.currentUserId();
        return R.ok(memberService.getDiscountRate(userId));
    }

    /** 订阅历史 */
    @GetMapping("/member/subscriptions")
    public R<List<MemberSubscriptionEntity>> subscriptions(@RequestParam Long userId) {
        return R.ok(memberService.listSubscriptions(userId));
    }

    /** 取消订阅（仅本人） */
    @PostMapping("/member/subscriptions/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam Long userId) {
        memberService.cancelSubscription(userId, id);
        return R.ok();
    }

    /** 开通订阅请求体 */
    public static class SubscribeRequest {
        private Long userId;
        private String tierCode;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getTierCode() {
            return tierCode;
        }

        public void setTierCode(String tierCode) {
            this.tierCode = tierCode;
        }
    }

    /** 会员专属空间视图 */
    @Data
    public static class MemberExclusiveView {
        private String welcome;
        private String tierName;
        private MemberService.MemberBenefits benefits;
    }
}

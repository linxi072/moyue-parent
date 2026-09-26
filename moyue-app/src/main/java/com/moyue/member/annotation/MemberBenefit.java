package com.moyue.member.annotation;

import com.moyue.member.service.MemberService;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员权益类型（用于 {@link RequiresMember#benefit()} 声明接口/方法所需的会员权益）。
 * 与 member_tier 表的 ad_free / discount_rate / badge 权益列一一对应。
 */
public enum MemberBenefit {

    /** 任意生效中会员（仅需 isActive） */
    MEMBER("会员"),
    /** 免广告权益（tier.adFree = 1） */
    AD_FREE("免广告"),
    /** 折扣权益（tier.discountRate < 1.00） */
    DISCOUNT("折扣"),
    /** 专属徽章权益（tier.badge 非空） */
    BADGE("专属徽章");

    private final String label;

    MemberBenefit(String label) {
        this.label = label;
    }

    /** 权益中文名（用于拒绝文案） */
    public String getLabel() {
        return label;
    }

    /**
     * 判断给定权益视图是否满足本权益要求。
     *
     * @param b 当前用户会员权益视图（null 视为非会员）
     * @return 是否满足
     */
    public boolean satisfiedBy(MemberService.MemberBenefits b) {
        if (b == null || !b.isActive()) {
            return false;
        }
        switch (this) {
            case MEMBER:
                return true;
            case AD_FREE:
                return b.isAdFree();
            case DISCOUNT:
                return b.getDiscountRate() != null
                        && b.getDiscountRate().compareTo(BigDecimal.ONE) < 0;
            case BADGE:
                List<String> badges = b.getBadges();
                return badges != null && !badges.isEmpty();
            default:
                return false;
        }
    }
}

package com.moyue.member.aspect;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.member.annotation.MemberBenefit;
import com.moyue.member.annotation.RequiresMember;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 会员权益拦截切面单测（P2-B 权益框架深化）。
 * 不依赖 Spring，直接调用 {@code checkMember} 校验「匿名/非会员/会员/细分权益」四类边界。
 */
@ExtendWith(MockitoExtension.class)
class MemberRequiredAspectTest {

    @Mock
    private MemberService memberService;

    @Mock
    private RequiresMember requiresMember;

    @InjectMocks
    private MemberRequiredAspect aspect;

    @AfterEach
    void clear() {
        SecurityContextHolder.clear();
    }

    private MemberService.MemberBenefits benefits(boolean active, boolean adFree, BigDecimal rate, String badge) {
        MemberService.MemberBenefits b = new MemberService.MemberBenefits();
        b.setActive(active);
        b.setAdFree(adFree);
        b.setDiscountRate(rate);
        b.setBadges(badge == null ? List.of() : List.of(badge));
        return b;
    }

    @Test
    void anonymous_rejectedWithUnauthorized() {
        SecurityContextHolder.clear();
        // 匿名分支在读取 benefit() 前即抛 UNAUTHORIZED，无需 stub benefit
        assertThatThrownBy(() -> aspect.checkMember(requiresMember))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void nonMember_rejectedWithMemberRequired() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(1L, 1));
        when(memberService.getBenefits(1L)).thenReturn(benefits(false, false, BigDecimal.ONE, null));
        when(requiresMember.benefit()).thenReturn(MemberBenefit.MEMBER);
        assertThatThrownBy(() -> aspect.checkMember(requiresMember))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.MEMBER_REQUIRED.getCode());
    }

    @Test
    void activeMember_passes() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(2L, 1));
        when(memberService.getBenefits(2L)).thenReturn(benefits(true, true, new BigDecimal("0.90"), "vip"));
        when(requiresMember.benefit()).thenReturn(MemberBenefit.MEMBER);
        assertThatCode(() -> aspect.checkMember(requiresMember)).doesNotThrowAnyException();
    }

    @Test
    void memberWithoutDiscount_rejectedForDiscountBenefit() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(3L, 1));
        when(memberService.getBenefits(3L)).thenReturn(benefits(true, false, BigDecimal.ONE, null));
        when(requiresMember.benefit()).thenReturn(MemberBenefit.DISCOUNT);
        assertThatThrownBy(() -> aspect.checkMember(requiresMember))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ResultCode.MEMBER_REQUIRED.getCode());
    }

    @Test
    void memberWithDiscount_passesForDiscountBenefit() {
        SecurityContextHolder.set(new SecurityContextHolder.LoginUser(4L, 1));
        when(memberService.getBenefits(4L)).thenReturn(benefits(true, false, new BigDecimal("0.90"), null));
        when(requiresMember.benefit()).thenReturn(MemberBenefit.DISCOUNT);
        assertThatCode(() -> aspect.checkMember(requiresMember)).doesNotThrowAnyException();
    }
}

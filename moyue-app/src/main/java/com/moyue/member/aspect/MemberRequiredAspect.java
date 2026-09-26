package com.moyue.member.aspect;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.member.annotation.MemberBenefit;
import com.moyue.member.annotation.RequiresMember;
import com.moyue.member.service.MemberService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 会员权益拦截切面：方法进入前校验 {@link RequiresMember} 声明的会员权益。
 *
 * <p>当前用户取自 {@link SecurityContextHolder}（由 HeaderInterceptor 从 X-User-Id 头装配），
 * 校验失败抛 {@link BizException}(MEMBER_REQUIRED / UNAUTHORIZED)，由 GlobalExceptionHandler 统一转 R.fail。</p>
 *
 * <p>与 PreAuthorizeAspect 同一机制（@Before + 注解绑定）。注意：AspectJ 在
 * {@code @annotation(x) || @within(x)} 的「或」组合下，当只有一个分支命中时入参 x 可能为空（绑定未定义），
 * 故拆成两个独立 advice 分别绑定方法级 / 类级注解，再共用纯校验方法 {@link #checkMember(RequiresMember)}，
 * 既避免 null 绑定 NPE，又保留类级 {@code @RequiresMember} 能力。会员状态经
 * {@link MemberService#getBenefits(Long)} 实时解析（其内部已同步到期态），保证权益判定与订阅有效期一致。</p>
 */
@Aspect
@Component
public class MemberRequiredAspect {

    private final MemberService memberService;

    @Autowired
    public MemberRequiredAspect(MemberService memberService) {
        this.memberService = memberService;
    }

    /** 方法级 @RequiresMember */
    @Before("@annotation(requiresMember)")
    public void checkMemberAnnotated(RequiresMember requiresMember) {
        checkMember(requiresMember);
    }

    /** 类级 @RequiresMember（整体会员专属 Controller） */
    @Before("@within(requiresMember)")
    public void checkMemberWithin(RequiresMember requiresMember) {
        checkMember(requiresMember);
    }

    /**
     * 纯会员权益校验：读 SecurityContextHolder + MemberService，不满足即抛 BizException。
     * 不依赖 JoinPoint，便于单元单测直接调用。
     */
    public void checkMember(RequiresMember requiresMember) {
        SecurityContextHolder.LoginUser user = SecurityContextHolder.get();
        if (user == null || user.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        MemberService.MemberBenefits benefits = memberService.getBenefits(user.getUserId());
        MemberBenefit required = requiresMember.benefit();
        if (!required.satisfiedBy(benefits)) {
            throw new BizException(ResultCode.MEMBER_REQUIRED,
                    "该功能仅「" + required.getLabel() + "」会员可用");
        }
    }
}

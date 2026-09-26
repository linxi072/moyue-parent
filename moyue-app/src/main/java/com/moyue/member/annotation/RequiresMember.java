package com.moyue.member.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 会员权益校验注解：标注在 Controller 类或方法上，由
 * {@link com.moyue.member.aspect.MemberRequiredAspect} 在进入方法前校验当前登录用户是否满足所需会员权益，
 * 不满足抛 {@code MEMBER_REQUIRED}（10003），由 {@code GlobalExceptionHandler} 统一转 R.fail。
 *
 * <pre>{@code
 * @RequiresMember                                  // 任意生效中会员可访问
 * @RequiresMember(benefit = MemberBenefit.DISCOUNT) // 仅享「折扣」权益的会员可访问
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresMember {

    /** 访问所需的会员权益，默认任意生效中会员 */
    MemberBenefit benefit() default MemberBenefit.MEMBER;
}

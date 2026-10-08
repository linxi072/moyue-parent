package com.moyue.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解：命中任一角色即放行（OR 语义）。
 *
 * <p>超管（superAdmin = true）恒放行。
 *
 * @author moyue
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRoles {

    /** 允许访问的角色标识 */
    String[] value();

    /** 不满足时的提示语 */
    String message() default "当前角色无权访问该资源";
}

package com.moyue.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解。
 *
 * <p>默认全部满足才放行（AND 语义）；设置 {@link #logical()} 为 OR 时命中任一即放行。
 *
 * @author moyue
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermissions {

    /** 权限标识，如 system:user:list */
    String[] value();

    /** 组合逻辑，默认 AND */
    Logical logical() default Logical.AND;

    /** 不满足时的提示语 */
    String message() default "无权限执行该操作";

    /** 组合逻辑枚举 */
    enum Logical {
        /** 全部满足 */
        AND,
        /** 命中任一 */
        OR
    }
}

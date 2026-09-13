package com.moyue.common.core.annotation;

import com.moyue.common.security.PreAuthorizeAspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解（对齐 RuoYi @RequiresRoles）：标注在 Controller 方法或类上，
 * 由 {@link PreAuthorizeAspect} 在进入方法前校验当前用户角色（网关 X-User-Role 头，1=读者 2=作者 3=管理员）。
 *
 * <pre>{@code
 * @RequiresRoles("3")                       // 仅管理员
 * @RequiresRoles(value = {"1", "2"})        // 读者或作者
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRoles {

    /** 允许访问的角色集合 */
    String[] value();
}

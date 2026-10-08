package com.moyue.common.security.aspect;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.common.security.annotation.RequiresRoles;
import com.moyue.common.security.context.UserContext;
import com.moyue.common.security.model.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 鉴权切面：处理 {@link RequiresRoles} 与 {@link RequiresPermissions}。
 *
 * <p>采用「方法优先、类兜底」的注解查找顺序（与 Spring 惯例一致），
 * 方法上没有注解时回退到类级别注解，便于整个 Controller 统一设权。
 *
 * @author moyue
 */
@Slf4j
@Aspect
public class AuthAspect {

    @Around("@within(com.moyue.common.security.annotation.RequiresRoles)"
            + " || @within(com.moyue.common.security.annotation.RequiresPermissions)"
            + " || @annotation(com.moyue.common.security.annotation.RequiresRoles)"
            + " || @annotation(com.moyue.common.security.annotation.RequiresPermissions)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = resolveMethod(point);

        RequiresRoles roles = AnnotatedElementUtils.findMergedAnnotation(method, RequiresRoles.class);
        if (roles == null && method.getDeclaringClass() != null) {
            roles = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequiresRoles.class);
        }
        RequiresPermissions perms =
                AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermissions.class);
        if (perms == null && method.getDeclaringClass() != null) {
            perms = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequiresPermissions.class);
        }

        if (roles == null && perms == null) {
            return point.proceed();
        }

        LoginUser user = UserContext.get();
        if (user == null) {
            throw BusinessException.unauthorized();
        }

        if (roles != null && !matchAny(user, roles.value())) {
            log.warn("角色校验失败 userId={} need={} has={}",
                    user.getUserId(), Arrays.toString(roles.value()), user.getRoleKeys());
            throw new BusinessException(ErrorCode.FORBIDDEN, roles.message());
        }

        if (perms != null && !matchPermissions(user, perms)) {
            log.warn("权限校验失败 userId={} need={}", user.getUserId(), Arrays.toString(perms.value()));
            throw new BusinessException(ErrorCode.FORBIDDEN, perms.message());
        }

        return point.proceed();
    }

    private Method resolveMethod(ProceedingJoinPoint point) {
        if (point.getSignature() instanceof MethodSignature ms) {
            Method method = ms.getMethod();
            // 代理类上取到的是接口方法，回退到目标类真实方法以读取类级注解
            if (method.getDeclaringClass().isInterface() && point.getTarget() != null) {
                try {
                    return point.getTarget().getClass()
                            .getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException ignored) {
                    return method;
                }
            }
            return method;
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "鉴权切面只能作用于方法");
    }

    private boolean matchAny(LoginUser user, String[] candidates) {
        if (candidates == null || candidates.length == 0) {
            return true;
        }
        return Arrays.stream(candidates).anyMatch(user::hasRole);
    }

    private boolean matchPermissions(LoginUser user, RequiresPermissions perms) {
        String[] values = perms.value();
        if (values == null || values.length == 0) {
            return true;
        }
        if (perms.logical() == RequiresPermissions.Logical.OR) {
            return Arrays.stream(values).anyMatch(user::hasPermission);
        }
        return Arrays.stream(values).allMatch(user::hasPermission);
    }

    /**
     * 后台路径守卫：{@value Constants#ADMIN_PATH_PREFIX} 下的接口要求运营身份。
     *
     * <p>该方法由 {@code AdminRoleInterceptor} 复用，集中在此避免规则分散。
     *
     * @param user 登录主体
     */
    public static void requireOperator(LoginUser user) {
        if (user == null) {
            throw BusinessException.unauthorized();
        }
        if (!user.isOperator()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "后台接口仅允许运营人员访问");
        }
    }
}

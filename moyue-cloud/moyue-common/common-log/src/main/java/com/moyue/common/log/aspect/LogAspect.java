package com.moyue.common.log.aspect;

import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.log.enums.OperatorType;
import com.moyue.common.log.model.OperLogDTO;
import com.moyue.common.log.sink.OperLogSink;
import com.moyue.common.log.util.IpUtils;
import com.moyue.common.security.context.UserContext;
import com.moyue.common.security.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面。
 *
 * <p>核心约束：<b>日志采集绝不能影响主流程</b>。所有采集逻辑包在 try/catch 中，
 * 异常仅记录不抛出；响应体仅在 {@code saveResponseData = true} 时截断保存（上限 2000 字符），
 * 避免大列表接口把日志表撑爆。
 *
 * @author moyue
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class LogAspect {

    private static final int MAX_PARAM_LEN = 4000;
    private static final int MAX_RESULT_LEN = 2000;

    private final OperLogSink sink;

    @Around("@annotation(com.moyue.common.log.annotation.Log)"
            + " || @within(com.moyue.common.log.annotation.Log)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Log annotation = resolveAnnotation(point);
        if (annotation == null || annotation.ignore()) {
            return point.proceed();
        }

        long start = System.currentTimeMillis();
        Throwable error = null;
        Object result = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            try {
                sink.save(build(point, annotation, result, error, System.currentTimeMillis() - start));
            } catch (Exception ex) {
                // 日志落库失败不影响业务
                log.warn("操作日志采集失败：{}", ex.getMessage());
            }
        }
    }

    private Log resolveAnnotation(ProceedingJoinPoint point) {
        if (!(point.getSignature() instanceof MethodSignature ms)) {
            return null;
        }
        Method method = ms.getMethod();
        Log ann = AnnotatedElementUtils.findMergedAnnotation(method, Log.class);
        if (ann == null) {
            ann = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Log.class);
        }
        return ann;
    }

    private OperLogDTO build(ProceedingJoinPoint point, Log annotation,
                             Object result, Throwable error, long cost) {
        HttpServletRequest request = currentRequest();
        MethodSignature ms = (MethodSignature) point.getSignature();
        Method method = ms.getMethod();

        LoginUser user = UserContext.get();
        BusinessType businessType = annotation.businessType();
        OperatorType operatorType = annotation.operatorType();
        if (operatorType == OperatorType.OTHER && user != null) {
            operatorType = OperatorType.of(user.getUserType());
        }

        String jsonResult = null;
        if (annotation.saveResponseData() && result instanceof R<?> r) {
            jsonResult = truncate(String.valueOf(r.getData()), MAX_RESULT_LEN);
        }

        return OperLogDTO.builder()
                .title(annotation.title())
                .businessType(businessType.getValue())
                .method(method.getDeclaringClass().getName() + "#" + method.getName())
                .requestMethod(request == null ? "-" : request.getMethod())
                .operatorType(operatorType.getValue())
                .operName(user == null ? "anonymous" : user.getUsername())
                .operId(user == null ? null : user.getUserId())
                .operUrl(request == null ? "-" : request.getRequestURI())
                .operIp(request == null ? "-" : IpUtils.getIp(request))
                .operParam(annotation.saveRequestData()
                        ? truncate(Arrays.toString(point.getArgs()), MAX_PARAM_LEN)
                        : null)
                .jsonResult(jsonResult)
                .status(error == null ? 0 : 1)
                .errorMsg(error == null ? null : truncate(error.getMessage(), MAX_RESULT_LEN))
                .costTime(cost)
                .operTime(LocalDateTime.now())
                .build();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...(truncated)";
    }
}

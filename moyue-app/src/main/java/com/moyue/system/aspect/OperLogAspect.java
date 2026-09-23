package com.moyue.system.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.security.SecurityContextHolder;
import com.moyue.system.annotation.Log;
import com.moyue.system.entity.SysOperLogEntity;
import com.moyue.system.service.OperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志切面：拦截标注 {@code @Log} 的 Controller 方法，组装并异步落库 sys_oper_log。
 * <ul>
 *   <li>正常返回：status=0，result 截断 2000 字符；</li>
 *   <li>抛出异常：status=1，记录 error_msg，异常照常向上抛（日志绝不吞异常）；</li>
 *   <li>param / result 截断 2000，防止 TEXT 字段写入超大 payload；</li>
 *   <li>切面自身任何异常都被 catch 并只打 warn——日志故障不影响业务。</li>
 * </ul>
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    /** TEXT 字段最大保留长度 */
    private static final int MAX_TEXT_LENGTH = 2000;

    /** 不参与参数序列化的类型（Servlet 对象 / 上传文件 / 校验结果） */
    private static final List<Class<?>> EXCLUDED_PARAM_TYPES = Arrays.asList(
            HttpServletRequest.class, HttpServletResponse.class, MultipartFile.class, BindingResult.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private OperLogService operLogService;

    /** 环绕拦截 @Log 注解方法 */
    @Around("@annotation(log)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperLogEntity operLog = new SysOperLogEntity();
        try {
            fillRequestInfo(operLog, logAnnotation);
            fillOperator(operLog);
            operLog.setParam(truncate(toJson(joinPoint.getArgs())));
        } catch (Exception e) {
            // 组装阶段异常不影响业务执行
            log.warn("[oper-log] 组装日志前置信息失败：{}", e.getMessage());
        }

        try {
            Object result = joinPoint.proceed();
            try {
                operLog.setStatus(0);
                operLog.setCostMs((int) (System.currentTimeMillis() - start));
                operLog.setResult(truncate(toJson(result)));
                operLog.setOperTime(LocalDateTime.now());
                operLogService.record(operLog);
            } catch (Exception e) {
                log.warn("[oper-log] 记录成功日志失败：{}", e.getMessage());
            }
            return result;
        } catch (Throwable e) {
            try {
                operLog.setStatus(1);
                operLog.setCostMs((int) (System.currentTimeMillis() - start));
                operLog.setErrorMsg(truncate(String.valueOf(e.getMessage())));
                operLog.setResult(null);
                operLog.setOperTime(LocalDateTime.now());
                operLogService.record(operLog);
            } catch (Exception ex) {
                log.warn("[oper-log] 记录失败日志失败：{}", ex.getMessage());
            }
            throw e;
        }
    }

    /** 填充 HTTP 请求上下文（method / url / ip）与注解元信息 */
    private void fillRequestInfo(SysOperLogEntity operLog, Log logAnnotation) {
        operLog.setModule(logAnnotation.module());
        operLog.setBusinessType(logAnnotation.businessType());
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            operLog.setRequestMethod(request.getMethod());
            operLog.setUrl(truncate(request.getRequestURI(), 255));
            operLog.setIp(resolveIp(request));
        }
    }

    /** 填充操作人（网关透传的 X-User-Id；登录场景无上下文则留空） */
    private void fillOperator(SysOperLogEntity operLog) {
        SecurityContextHolder.LoginUser user = SecurityContextHolder.get();
        if (user != null) {
            operLog.setOperatorId(user.getUserId());
            operLog.setOperatorName(user.getUserId() == null ? null : String.valueOf(user.getUserId()));
        }
    }

    /** 取客户端真实 IP（优先 X-Forwarded-For / X-Real-IP） */
    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /** 参数序列化：过滤 Servlet / 文件类参数，失败退化为数组字符串 */
    private String toJson(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            List<Object> filtered = Arrays.stream(args)
                    .filter(a -> a != null && EXCLUDED_PARAM_TYPES.stream().noneMatch(t -> t.isInstance(a)))
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            return Arrays.toString(args);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /** 截断到 2000 字符 */
    private String truncate(String text) {
        return truncate(text, MAX_TEXT_LENGTH);
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}

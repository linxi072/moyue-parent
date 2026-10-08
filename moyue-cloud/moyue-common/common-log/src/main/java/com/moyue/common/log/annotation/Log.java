package com.moyue.common.log.annotation;

import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.log.enums.OperatorType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 *
 * <p>标注在 Controller 方法上，由 {@code LogAspect} 采集后交给 {@code OperLogSink} 落库。
 * 采集失败不影响主流程（切面内部 try/catch），这是日志类横切逻辑的硬要求。
 *
 * @author moyue
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 模块标题，如「用户管理」 */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 操作人类别，默认按当前登录主体推断 */
    OperatorType operatorType() default OperatorType.OTHER;

    /** 是否保存请求参数 */
    boolean saveRequestData() default true;

    /** 是否保存响应结果（大列表接口建议关闭） */
    boolean saveResponseData() default false;

    /** 是否忽略（临时关闭采集时使用） */
    boolean ignore() default false;
}

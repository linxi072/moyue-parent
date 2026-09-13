package com.moyue.system.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（对齐 RuoYi @Log）：标注在需要落操作日志的 Controller 写操作方法上，
 * 由 {@code OperLogAspect} 拦截并异步写入 sys_oper_log。
 *
 * <pre>{@code
 * @Log(module = "字典管理", businessType = BusinessType.INSERT)
 * }</pre>
 *
 * <p>查询类接口不加该注解，避免日志量暴涨；异常时切面记录 error_msg 并置 status=1。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Log {

    /** 业务模块名称，如 字典管理 */
    String module();

    /** 业务类型，取值见 {@link BusinessType} */
    int businessType();

    /** 业务类型常量（对齐 RuoYi BusinessType 枚举语义） */
    interface BusinessType {

        /** 其它 */
        int OTHER = 0;

        /** 新增 */
        int INSERT = 1;

        /** 修改 */
        int UPDATE = 2;

        /** 删除 */
        int DELETE = 3;

        /** 导出 */
        int EXPORT = 4;

        /** 强退（在线用户强制下线） */
        int FORCE = 5;

        /** 生成代码 */
        int GENCODE = 6;
    }
}

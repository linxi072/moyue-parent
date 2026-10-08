package com.moyue.common.core.config;

import com.moyue.common.core.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * common-core 自动装配。
 *
 * <p>Cloud 各服务启动时只会扫描自身启动类所在包（{@code com.moyue.content} / {@code com.moyue.social} …），
 * <b>不会</b>像单体那样扫 {@code com.moyue} 全包，因此通用组件必须显式注册，否则静默失效。
 *
 * <p>{@link GlobalExceptionHandler} 是典型例子：未注册时业务异常没有任何 {@code @ExceptionHandler} 接住，
 * 会以 HTTP 500 暴露出去，前端既拿不到业务码也拿不到提示语，与架构约定（HTTP 200 + code != 0）相悖。
 *
 * @author moyue
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class CoreAutoConfiguration {
}

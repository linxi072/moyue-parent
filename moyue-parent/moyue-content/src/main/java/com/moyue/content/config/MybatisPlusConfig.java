package com.moyue.content.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置：注册分页拦截器，使 selectPage 真正生效。
 * <p>模块合并说明：原 moyue-book / moyue-chapter / moyue-read 各有一份内容完全一致的
 * {@code MybatisPlusConfig}，同进程加载会因同名 {@code mybatisPlusInterceptor} Bean 触发
 * {@code BeanDefinitionOverrideException}，故收敛为本模块唯一一份（包名 {@code com.moyue.content.config}）。
 * 四个源模块的 {@code com.moyue.{book,chapter,read}.config.MybatisPlusConfig} 已删除。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

package com.moyue.system.config;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Velocity 模板引擎配置（代码生成域 ⑮）。
 *
 * <p>模板统一放在 {@code resources/vm/} 下，用 classpath 加载，便于随 jar 分发。
 *
 * @author moyue
 */
@Configuration
public class VelocityEngineConfig {

    @Bean
    public VelocityEngine velocityEngine() {
        Properties props = new Properties();
        props.setProperty(RuntimeConstants.RESOURCE_LOADERS, "class");
        props.setProperty("resource.loader.class.class", ClasspathResourceLoader.class.getName());
        props.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
        // Velocity 2.x 已移除 OUTPUT_ENCODING：渲染结果一律走 StringWriter，
        // 编码由上层写文件时决定（GenTableServiceImpl 统一 UTF-8）。
        VelocityEngine engine = new VelocityEngine(props);
        engine.init();
        return engine;
    }
}

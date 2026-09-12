package com.moyue.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 网关启动类。
 * 扫描范围设为 com.moyue，以加载通用模块（JWT / 常量）与本模块组件。
 * 启用服务发现以解析 lb:// 路由目标。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

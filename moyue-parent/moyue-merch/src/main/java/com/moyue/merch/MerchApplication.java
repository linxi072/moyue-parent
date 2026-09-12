package com.moyue.merch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 商城周边服务启动类。
 * 统一扫描 com.moyue 以复用 common 中的组件（R / BizException / AdminRoleInterceptor）。
 * 本服务暂无跨服务调用，不启用 Feign。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
public class MerchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchApplication.class, args);
    }
}

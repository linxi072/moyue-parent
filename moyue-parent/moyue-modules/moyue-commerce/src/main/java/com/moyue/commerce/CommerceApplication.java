package com.moyue.commerce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 商业域服务：积分 points / 周边商城 merch / 作者稿酬 author（moyue-commerce，端口 8084）。
 *
 * <p>RuoYi 目录风格重构：业务模块归入 moyue-modules/ 聚合目录，Java 包名与 HTTP 路径零变更。
 * 统一扫描 com.moyue 根包以复用 common / api 组件与 Feign 客户端。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class CommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceApplication.class, args);
    }
}

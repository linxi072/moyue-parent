package com.moyue.risk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 内容安全域服务：敏感词 / 机审 / 举报 / 人工审核 audit（moyue-risk，端口 8089）。
 *
 * <p>RuoYi 目录风格重构：业务模块归入 moyue-modules/ 聚合目录，Java 包名与 HTTP 路径零变更。
 * 统一扫描 com.moyue 根包以复用 common / api 组件与 Feign 客户端。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class RiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskApplication.class, args);
    }
}

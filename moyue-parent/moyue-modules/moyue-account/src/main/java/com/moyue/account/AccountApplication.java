package com.moyue.account;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 账号域启动类（认证 + 用户资料）。
 * 由 moyue-auth 与 moyue-user 合并而来：统一端口 8081，服务名 moyue-account。
 * 启用 MyBatis-Plus 扫描、Nacos 服务发现与 Feign 客户端（复用 moyue-api 中定义）。
 * 扫描整个 com.moyue 根包，故 com.moyue.auth / com.moyue.user 包名保持原样。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}

package com.moyue.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 系统管理服务启动类（8086）。
 *
 * <p>承载十六域 118 个端点：组织权限组（①~⑥）/ 审计日志组（⑦~⑨）/ 运维工具组（⑩~⑭）/ 低代码组（⑮⑯）。
 *
 * <p>架构说明书 3.2 重构约束：启动类统一 {@code @ComponentScan("com.moyue")} + {@code @MapperScan("com.moyue.system.mapper")}，
 * 保证 Java 包名与 HTTP 路径零变更，两仓（cloud / boot）共用同一套业务代码。
 *
 * @author moyue
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue.system.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.moyue")
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}

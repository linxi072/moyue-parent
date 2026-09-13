package com.moyue.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 管理端服务启动类（审核 audit / 运营公告与对账 operation / 统计 stat / 系统管理 system / XXL-Job job）（moyue-admin，端口 8093）。
 *
 * <p>三端重构（读者端 / 作者端 / 管理端）：多个领域模块聚合进本服务，各能力域的 Java 包名
 * 保持 com.moyue.{域} 原样不变，统一扫描 com.moyue 根包以复用 common / api 组件与 Feign 客户端。
 * 本服务承载 XXL-Job 执行器（appname=moyue-job，RPC 9099）与系统管理 BCrypt 加密。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

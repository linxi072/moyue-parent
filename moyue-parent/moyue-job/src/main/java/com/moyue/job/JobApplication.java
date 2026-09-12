package com.moyue.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 任务调度执行器启动类（XXL-Job 2.4.0）。
 * 统一扫描 com.moyue 复用 common / api 中的 Feign 客户端（聚合统计任务演示跨服务调用）。
 * 注意：本模块无数据库访问，不引入 MyBatis / @MapperScan。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}

package com.moyue.social;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 社区域服务启动类（评论 / 博客 / 即时通讯 / 站内信）。
 * 由原 moyue-comment、moyue-blog、moyue-im、moyue-message 四模块合并而来，
 * 各业务包名（com.moyue.comment / blog / im / message）保持不变。
 * 统一扫描 com.moyue 以复用 common / api 中的组件与 Feign 客户端。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class SocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApplication.class, args);
    }
}

package com.moyue.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 认证服务启动类（登录 / 注册 / 刷新令牌，moyue-auth，端口 8090）。
 *
 * <p>对齐 RuoYi ruoyi-auth 独立认证模式：从账号域拆出，专责 JWT 签发与刷新；
 * 用户资料管理仍在 moyue-account。auth_user 表由共享 Flyway 迁移维护。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

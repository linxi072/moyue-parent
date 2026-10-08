package com.moyue.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 认证服务启动类（8090）。
 *
 * <p><b>为什么独立成服务</b>（架构说明书 2.x ADR-3）：登录 / 注册 / 刷新令牌与用户资料
 * 解耦，便于独立扩容与限流 —— 登录是典型的「读少写多但峰值集中」流量，
 * 与后台管理接口混部会互相拖累。
 *
 * <p><b>为什么自己查库而不是调 moyue-system</b>：认证在链路最上游，若依赖下游服务
 * 才能完成登录，一旦 system 不可用则全站无法登录（循环依赖 + 雪崩）。故 auth 以
 * 只读方式直接访问 sys_user 等表，不反向依赖任何业务模块。
 *
 * @author moyue
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
@MapperScan("com.moyue.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

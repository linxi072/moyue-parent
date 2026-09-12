package com.moyue.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 平台域启动类（moyue-platform，端口 8086）。
 *
 * <p>由原 moyue-audit / moyue-operation / moyue-stat / moyue-job 四模块合并而成，
 * 承载四块职责：内容审核（audit）、运营公告与打赏订单（operation）、
 * 全站统计（stat）、XXL-Job 定时任务（job）。</p>
 *
 * <p>统一扫描 com.moyue 以复用 common / api 中的组件与 Feign 客户端；
 * 四个源模块的包名（com.moyue.audit / operation / stat / job）原样保留，未做迁移。
 * 平台域有数据源，故保留 @MapperScan。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}

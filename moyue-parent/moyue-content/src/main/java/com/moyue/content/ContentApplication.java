package com.moyue.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 内容域服务启动类（模块收敛：moyue-book / moyue-chapter / moyue-read / moyue-search 四合一）。
 * <p>统一扫描 {@code com.moyue} 根包，以复用 moyue-common / moyue-api 中的组件与 Feign 客户端；
 * 四个源模块的包名保持 {@code com.moyue.book} / {@code com.moyue.chapter} / {@code com.moyue.read} /
 * {@code com.moyue.search} 不变，故合并无需改动任何业务类的包名。</p>
 * <p>启用 MyBatis-Plus 扫描（book / chapter / bookshelf 三域 Mapper）、Nacos 服务发现与 Feign
 * （解析作者昵称、校验作品归属、发放阅读积分）。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class ContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
    }
}

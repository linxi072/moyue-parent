package com.moyue.author;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 作者端服务启动类（作品 book / 章节 chapter / 作者稿酬 author / 封面文件 content.config）（moyue-author，端口 8092）。
 *
 * <p>三端重构（读者端 / 作者端 / 管理端）：多个领域模块聚合进本服务，各能力域的 Java 包名
 * 保持 com.moyue.{域} 原样不变，统一扫描 com.moyue 根包以复用 common / api 组件与 Feign 客户端。
 * 作品 CRUD 经 Feign 同步检索索引至 moyue-reader（SearchIndexClient），发布前经 Feign 送 moyue-admin 机审（RiskClient）。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class AuthorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorApplication.class, args);
    }
}

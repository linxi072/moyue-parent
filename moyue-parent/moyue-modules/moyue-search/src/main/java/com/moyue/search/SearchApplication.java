package com.moyue.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 检索域服务：书籍全文检索 / 热门推荐（只读 Elasticsearch，索引 moyue_book）（moyue-search，端口 8085）。
 *
 * <p>RuoYi 目录风格重构：业务模块归入 moyue-modules/ 聚合目录，Java 包名与 HTTP 路径零变更。
 * 统一扫描 com.moyue 根包以复用 common / api 组件与 Feign 客户端。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}

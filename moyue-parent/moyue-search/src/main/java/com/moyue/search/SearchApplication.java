package com.moyue.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 搜索引擎服务启动类。
 * 纯 Elasticsearch 检索服务：无 MySQL 数据源，索引文档由 book 服务
 * 经 /internal/search/books/_index 推送同步；统一扫描 com.moyue 复用 common 组件。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@EnableDiscoveryClient
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}

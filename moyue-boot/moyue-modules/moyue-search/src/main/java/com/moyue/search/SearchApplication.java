package com.moyue.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 检索服务启动类（8085）。
 *
 * <p><b>脚手架占位</b>：本模块本轮只落工程骨架（启动类 + 配置 + 健康检查端点），
 * 业务域代码按开发计划 V3.0 的迭代排期后续补齐。
 *
 * <p>ES 全文检索 / 分类筛选 / 热门推荐（只读）
 *
 * @author moyue
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
@MapperScan("com.moyue.search.mapper")
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}

package com.moyue.reader;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 读者端服务启动类（阅读书架 read / 评论 comment / 博客 blog / IM im / 积分 points / 商城 merch / 搜索 search / 触达 message / 打赏稿酬 operation-Reward）（moyue-reader，端口 8091）。
 *
 * <p>三端重构（读者端 / 作者端 / 管理端）：多个领域模块聚合进本服务，各能力域的 Java 包名
 * 保持 com.moyue.{域} 原样不变，统一扫描 com.moyue 根包以复用 common / api 组件与 Feign 客户端。
 * 本服务承载检索（Elasticsearch 只读）与邮件触达（JavaMail），有数据源故启用 @MapperScan。</p>
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class ReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReaderApplication.class, args);
    }
}

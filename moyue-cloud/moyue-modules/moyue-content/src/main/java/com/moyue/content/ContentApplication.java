package com.moyue.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 内容服务启动类（8082）。
 *
 * <p><b>脚手架占位</b>：本模块本轮只落工程骨架（启动类 + 配置 + 健康检查端点），
 * 业务域代码按开发计划 V3.0 的迭代排期后续补齐。
 *
 * <p>作品 / 章节 / 阅读进度 / 书架 / 封面文件
 *
 * @author moyue
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
@MapperScan("com.moyue.content.mapper")
public class ContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
    }
}

package com.moyue.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;

/**
 * AI 服务启动类（8097）。
 *
 * <p><b>脚手架占位</b>：本模块本轮只落工程骨架（启动类 + 配置 + 健康检查端点），
 * 业务域代码按开发计划 V3.0 的迭代排期后续补齐。
 *
 * <p>AI 摘要 / 智能推荐 / 内容辅助
 *
 * @author moyue
 */
@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
@MapperScan("com.moyue.ai.mapper")
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}

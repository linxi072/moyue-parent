package com.moyue.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 智能客服服务启动类。
 * 统一扫描 com.moyue 以复用 common 中的组件。
 * 回复引擎为可插拔接口 AiReplyEngine：默认关键词规则引擎，后续可替换为真实大模型适配器。
 * 每轮问答落库后经 Feign（SearchIndexClient）异步推送 moyue-search 建 ES 索引。
 */
@SpringBootApplication
@ComponentScan("com.moyue")
@MapperScan("com.moyue")
@EnableDiscoveryClient
@EnableFeignClients("com.moyue")
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}

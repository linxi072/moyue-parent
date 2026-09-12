package com.moyue.content.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch Repository 启用配置。
 * <p>合并前 moyue-search 未在任何位置声明 {@code @EnableElasticsearchRepositories}，
 * 其 {@code BookSearchRepository} 依赖 Spring Boot 对启动类所在包的默认扫描
 * （{@code SearchApplication} 位于 {@code com.moyue.search}，repository 在其子包下）才得以装配。</p>
 * <p>合并后启动类变为 {@code com.moyue.content.ContentApplication}，若仍依赖默认包推断，
 * Repository 扫描基准会落在 {@code com.moyue.content}，导致 search 域 Repository 静默丢失。
 * 故这里显式把扫描基准钉到 {@code com.moyue.search.repository}，与包名保持不变的约定一致。</p>
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.moyue.search.repository")
public class ElasticsearchRepositoryConfig {
}

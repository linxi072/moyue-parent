package com.moyue.search.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch Repository 启用配置（moyue-search）。
 *
 * <p>P2-13 由 {@code com.moyue.content.config.ElasticsearchRepositoryConfig} 迁入并改包。
 * 显式把扫描基准钉到 {@code com.moyue.search.repository}（与「迁移后包名保持不变」的约定一致），
 * 不依赖启动类所在包的默认推断，避免 Repository 静默丢失。</p>
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.moyue.search.repository")
public class ElasticsearchRepositoryConfig {
}

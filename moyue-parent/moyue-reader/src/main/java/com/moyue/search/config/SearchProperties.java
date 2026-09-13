package com.moyue.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索域配置（前缀 {@code moyue.search}）。
 *
 * <p>对应 application.yml 中的 {@code moyue.search.*}；所有值均可由环境变量 /
 * Nacos Config 覆盖，不在代码中硬编码业务参数。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "moyue.search")
public class SearchProperties {

    /** 检索索引名（默认 moyue_book；与 {@code @Document(indexName=...)} 保持一致） */
    private String indexName = "moyue_book";

    /** 默认分页大小（请求未显式指定 size 时使用） */
    private int defaultPageSize = 20;

    /** 推荐位相关配置 */
    private Recommend recommend = new Recommend();

    /** 推荐位配置 */
    @Data
    public static class Recommend {

        /** 推荐位单次最大返回条数（limit 上限，默认 50） */
        private int maxLimit = 50;
    }
}

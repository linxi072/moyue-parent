package com.moyue.config;

import com.moyue.search.repository.BookSearchRepository;
import com.moyue.search.repository.ChapterSearchRepository;
import com.moyue.search.repository.QaSearchRepository;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 测试专用配置：单体集成测试环境无运行中的 Elasticsearch（项目硬性约束禁用 Docker，
 * 且 QA 约定 ES 用 mock 单测）。Spring Data ES 仓储会在上下文启动时 eager 连接 ES 并因
 * Connection refused 导致全部 {@code @SpringBootTest} 上下文加载失败；此处仅存在于 test
 * classpath，以 Mockito 桩替换三个仓储接口满足依赖注入，业务链路不触碰真实实例，不影响生产。
 */
@Configuration
public class ElasticsearchMockRepositoriesConfig {

    @Bean
    public BookSearchRepository bookSearchRepository() {
        return Mockito.mock(BookSearchRepository.class);
    }

    @Bean
    public ChapterSearchRepository chapterSearchRepository() {
        return Mockito.mock(ChapterSearchRepository.class);
    }

    @Bean
    public QaSearchRepository qaSearchRepository() {
        return Mockito.mock(QaSearchRepository.class);
    }
}

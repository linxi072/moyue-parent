package com.moyue.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** RAG 召回片段数（AI 客服知识库注入，默认 3） */
    private int ragTopK = 3;

    /** 推荐位相关配置 */
    private Recommend recommend = new Recommend();

    /**
     * 同义词词典：key → 等价词列表（双向扩展）。
     * 例：{@code 玄幻: [修真, 奇幻]} 表示搜「玄幻」也召回含「修真/奇幻」的文档，反之亦然。
     * yml 未配置时使用 {@link #defaultSynonyms()} 内置默认集。
     */
    private Map<String, List<String>> synonyms = defaultSynonyms();

    /**
     * 纠错词典：已知正确词集合（书名/分类名常见写法）。
     * 检索无命中时按编辑距离取最近词做纠错建议。yml 未配置时使用 {@link #defaultSpellDictionary()}。
     */
    private List<String> spellDictionary = defaultSpellDictionary();

    /** 推荐位配置 */
    @Data
    public static class Recommend {

        /** 推荐位单次最大返回条数（limit 上限，默认 50） */
        private int maxLimit = 50;
    }

    /** null-safe：yml 未配置或显式置 null 时回退内置默认集 */
    public Map<String, List<String>> getSynonyms() {
        return synonyms != null ? synonyms : defaultSynonyms();
    }

    /** null-safe：yml 未配置或显式置 null 时回退内置默认纠错词典 */
    public List<String> getSpellDictionary() {
        return spellDictionary != null ? spellDictionary : defaultSpellDictionary();
    }

    /** 内置默认同义词集（可被 yml 整体覆盖） */
    private static Map<String, List<String>> defaultSynonyms() {
        Map<String, List<String>> m = new HashMap<>();
        m.put("玄幻", Arrays.asList("修真", "奇幻", "仙侠"));
        m.put("都市", Arrays.asList("现实", "职场"));
        m.put("悬疑", Arrays.asList("推理", "悬疑推理"));
        m.put("武侠", Arrays.asList("江湖", "传统武侠"));
        m.put("科幻", Arrays.asList("硬科幻", "软科幻"));
        return m;
    }

    /** 内置默认纠错词典（分类名常见写法，可被 yml 整体覆盖） */
    private static List<String> defaultSpellDictionary() {
        return Arrays.asList("玄幻", "修真", "奇幻", "仙侠", "都市", "现实", "职场", "悬疑",
                "推理", "武侠", "江湖", "科幻", "历史", "军事", "游戏", "轻小说", "女生");
    }
}

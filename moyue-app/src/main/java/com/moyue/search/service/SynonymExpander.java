package com.moyue.search.service;

import com.moyue.search.config.SearchProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同义词扩展器（P1-5 检索质量调优）。
 *
 * <p>将用户检索词按配置词典 {@link SearchProperties#getSynonyms()} 双向扩展为等价词集合，
 * 供 {@link SearchService} 构建 bool.should 多词召回，提升同义查询的召回率。
 * 例：搜「玄幻」→ {玄幻, 修真, 奇幻, 仙侠}；搜「修真」→ {修真, 玄幻, 奇幻, 仙侠}（反向命中）。</p>
 */
@Component
public class SynonymExpander {

    private final SearchProperties searchProperties;

    public SynonymExpander(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
    }

    /** 双向扩展：保留原词，并集 key 的等价词与「原词作为 value 命中」的整组等价词 */
    public Set<String> expand(String keyword) {
        Set<String> out = new LinkedHashSet<>();
        if (keyword == null || keyword.isBlank()) {
            return out;
        }
        out.add(keyword);
        Map<String, List<String>> synonyms = searchProperties.getSynonyms();
        if (synonyms == null || synonyms.isEmpty()) {
            return out;
        }
        // 原词是 key：并入其等价词
        List<String> direct = synonyms.get(keyword);
        if (direct != null) {
            out.addAll(direct);
        }
        // 原词是某条目的 value：并入该组 key + 其余 value（整组召回）
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(keyword)) {
                out.add(entry.getKey());
                out.addAll(entry.getValue());
                break;
            }
        }
        return out;
    }
}

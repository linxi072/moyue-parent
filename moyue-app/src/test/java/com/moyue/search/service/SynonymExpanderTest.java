package com.moyue.search.service;

import com.moyue.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SynonymExpander 纯单测（无 Spring / ES）。
 */
class SynonymExpanderTest {

    private SynonymExpander expanderWith(Map<String, List<String>> synonyms) {
        SearchProperties props = new SearchProperties();
        props.setSynonyms(synonyms);
        return new SynonymExpander(props);
    }

    @Test
    void expandsKeyToEquivalents() {
        SynonymExpander e = expanderWith(Map.of("玄幻", List.of("修真", "奇幻")));
        Set<String> out = e.expand("玄幻");
        assertEquals(Set.of("玄幻", "修真", "奇幻"), out);
    }

    @Test
    void expandsValueBidirectionally() {
        SynonymExpander e = expanderWith(Map.of("玄幻", List.of("修真", "奇幻")));
        // 搜「修真」反向命中整组：修真 + 玄幻 + 奇幻
        Set<String> out = e.expand("修真");
        assertEquals(Set.of("修真", "玄幻", "奇幻"), out);
    }

    @Test
    void noSynonym_returnsOriginalOnly() {
        SynonymExpander e = expanderWith(Map.of("玄幻", List.of("修真")));
        assertEquals(Set.of("都市"), e.expand("都市"));
    }

    @Test
    void blankKeyword_returnsEmpty() {
        SynonymExpander e = expanderWith(Map.of("玄幻", List.of("修真")));
        assertTrue(e.expand("  ").isEmpty());
        assertTrue(e.expand(null).isEmpty());
    }

    @Test
    void usesDefaultDictionaryWhenConfigEmpty() {
        // 不设置 synonyms → 走内置默认集（玄幻→修真/奇幻/仙侠）
        SearchProperties props = new SearchProperties();
        props.setSynonyms(null);
        SynonymExpander e = new SynonymExpander(props);
        assertTrue(e.expand("玄幻").contains("修真"));
        assertTrue(e.expand("修真").contains("玄幻"));
    }
}

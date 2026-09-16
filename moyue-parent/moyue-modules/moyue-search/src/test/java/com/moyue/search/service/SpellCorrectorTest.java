package com.moyue.search.service;

import com.moyue.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SpellCorrector 纯单测（无 Spring / ES）。
 */
class SpellCorrectorTest {

    private SpellCorrector correctorWith(List<String> dict) {
        SearchProperties props = new SearchProperties();
        props.setSpellDictionary(dict);
        return new SpellCorrector(props);
    }

    @Test
    void inDictionary_returnsNull() {
        SpellCorrector c = correctorWith(Arrays.asList("玄幻", "修真"));
        assertNull(c.correct("玄幻"));
    }

    @Test
    void singleCharTypo_corrects() {
        SpellCorrector c = correctorWith(Arrays.asList("玄幻", "修真", "悬疑", "推理"));
        // 「玄幼」与「玄幻」编辑距离 1 → 纠错为玄幻
        assertEquals("玄幻", c.correct("玄幼"));
    }

    @Test
    void farTypo_returnsNull() {
        SpellCorrector c = correctorWith(Arrays.asList("玄幻", "修真"));
        // 「zzz」与任何词距离过大 → 不纠错
        assertNull(c.correct("zzz"));
    }

    @Test
    void blank_returnsNull() {
        SpellCorrector c = correctorWith(Arrays.asList("玄幻"));
        assertNull(c.correct(null));
        assertNull(c.correct("  "));
        assertNull(c.correct("a"));
    }

    @Test
    void emptyDictionary_returnsNull() {
        // 显式配置为空列表（非「未配置」）→ 不纠错
        SpellCorrector c = correctorWith(List.of());
        assertNull(c.correct("玄幼"));
    }

    @Test
    void nullDictionary_fallsBackToDefault() {
        // 未配置（null）→ 回退内置默认词典，仍纠错
        SpellCorrector c = correctorWith(null);
        assertEquals("玄幻", c.correct("玄幼"));
    }

    @Test
    void levenshteinDistance() {
        assertEquals(0, SpellCorrector.levenshtein("玄幻", "玄幻"));
        assertEquals(1, SpellCorrector.levenshtein("玄幼", "玄幻"));
        assertEquals(2, SpellCorrector.levenshtein("都市", "玄幻"));
    }
}

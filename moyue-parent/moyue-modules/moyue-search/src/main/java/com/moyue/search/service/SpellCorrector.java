package com.moyue.search.service;

import com.moyue.search.config.SearchProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 纠错词典（P1-5 检索质量调优）。
 *
 * <p>当主检索无命中时，按 {@link SearchProperties#getSpellDictionary()} 已知词集合，
 * 用 Levenshtein 编辑距离取最近词作为纠错建议（阈值随词长自适应），由
 * {@link SearchService#searchWithCorrection} 触发二次检索并回显 {@code correctedKeyword}。</p>
 */
@Component
public class SpellCorrector {

    /** 最大允许编辑距离（短词更严格） */
    private static final int MAX_DISTANCE = 2;

    private final SearchProperties searchProperties;

    public SpellCorrector(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
    }

    /** 纠错：命中词典返回 null；否则返回距离最近的词（≤ 阈值），否则 null */
    public String correct(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        Set<String> dict = toSet(searchProperties.getSpellDictionary());
        if (dict.isEmpty()) {
            return null;
        }
        if (dict.contains(keyword)) {
            return null; // 已在词典中，视为正确
        }
        if (keyword.length() <= 1) {
            return null;
        }
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String term : dict) {
            int d = levenshtein(keyword, term);
            if (d < bestDist) {
                bestDist = d;
                best = term;
            }
        }
        int threshold = Math.min(MAX_DISTANCE, keyword.length() / 3 + 1);
        return bestDist <= threshold ? best : null;
    }

    private Set<String> toSet(List<String> list) {
        return list == null ? new HashSet<>() : new HashSet<>(list);
    }

    /** Levenshtein 编辑距离（标准 DP 实现） */
    static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}

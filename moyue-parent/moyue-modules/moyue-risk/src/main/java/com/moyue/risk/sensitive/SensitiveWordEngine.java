package com.moyue.risk.sensitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词匹配引擎：自研 Trie/DFA（Aho-Corasick 简化版，零外部依赖）。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>构建：把全部敏感词插入 Trie，BFS 构建 fail 失配指针，并把 fail 节点的
 *       词尾集合向上合并到当前节点（output 链），一次扫描即可找出全部命中；</li>
 *   <li>匹配：主文本逐字符推进，失配沿 fail 回退（均摊 O(n)），命中即收集词与等级；</li>
 *   <li>刷新：{@link #reload(Collection)} 以「构建快照 + volatile 整体替换」方式热更新，
 *       刷新期间旧词库继续对外服务，无锁读、无中间态。</li>
 * </ul>
 */
public class SensitiveWordEngine {

    /** 等级：1 拦截（REJECT） */
    public static final int LEVEL_BLOCK = 1;

    /** 等级：2 告警（转人工 REVIEW） */
    public static final int LEVEL_WARN = 2;

    /** 单次匹配最多返回的命中词数（防御超长文本 + 巨量命中拖垮响应） */
    private static final int MAX_HITS = 100;

    /** Trie 节点 */
    private static final class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private TrieNode fail;
        /** 以当前节点结尾的敏感词集合（已合并 fail 链上的输出） */
        private Map<String, Integer> outputs;
    }

    /** 根节点 + 词→等级快照（volatile 整体替换，读侧无锁） */
    private volatile TrieNode root = newRoot();
    private volatile Map<String, Integer> wordLevels = Map.of();

    /**
     * 重建匹配快照（热刷新入口）。
     *
     * @param words 全量启用的敏感词（调用方保证 status=1 且未删除）
     */
    public synchronized void reload(Collection<SensitiveWordEntity> words) {
        TrieNode newRoot = newRoot();
        Map<String, Integer> levels = new LinkedHashMap<>();
        if (words != null) {
            for (SensitiveWordEntity w : words) {
                if (w == null || w.getWord() == null || w.getWord().isBlank()) {
                    continue;
                }
                String word = w.getWord().trim();
                int level = w.getLevel() == null ? LEVEL_BLOCK : w.getLevel();
                // 同词重复出现时保留更高等级（1 拦截优先于 2 告警）
                levels.merge(word, level, (oldV, newV) -> Math.min(oldV, newV));
                insert(newRoot, word, level);
            }
        }
        buildFailLinks(newRoot);
        this.root = newRoot;
        this.wordLevels = Collections.unmodifiableMap(levels);
    }

    /**
     * 匹配文本，返回命中的敏感词与等级（同一词去重，最多 {@value #MAX_HITS} 个）。
     *
     * @param text 待审文本（标题 + 正文），null/空直接返回空列表
     * @return 命中列表，按首次出现顺序
     */
    public List<Hit> match(String text) {
        List<Hit> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return hits;
        }
        TrieNode current = root;
        Map<String, Integer> levels = wordLevels;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 失配沿 fail 链回退，直到根或可转移
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            TrieNode next = current.children.get(c);
            if (next != null) {
                current = next;
            }
            if (current.outputs != null && !current.outputs.isEmpty()) {
                for (Map.Entry<String, Integer> out : current.outputs.entrySet()) {
                    String word = out.getKey();
                    // 词→等级以 wordLevels 快照为准（比节点上合并的更权威）
                    int level = levels.getOrDefault(word, out.getValue());
                    if (seen.add(word)) {
                        hits.add(new Hit(word, level));
                        if (hits.size() >= MAX_HITS) {
                            return hits;
                        }
                    }
                }
            }
        }
        return hits;
    }

    /** 当前词库规模（启用词数） */
    public int size() {
        return wordLevels.size();
    }

    // ------------------------------ 内部构建 ------------------------------

    private static TrieNode newRoot() {
        TrieNode root = new TrieNode();
        root.fail = root;
        return root;
    }

    /** 插入一个敏感词到 Trie（词尾挂 outputs，含等级） */
    private static void insert(TrieNode root, String word, int level) {
        TrieNode current = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            current = current.children.computeIfAbsent(c, k -> new TrieNode());
        }
        if (current.outputs == null) {
            current.outputs = new LinkedHashMap<>();
        }
        current.outputs.put(word, level);
    }

    /** BFS 构建 fail 指针，并把 fail 节点的输出合并到当前节点（Aho-Corasick 简化版核心） */
    private static void buildFailLinks(TrieNode root) {
        List<TrieNode> queue = new ArrayList<>();
        // 第一层：fail 全部指向根
        for (TrieNode child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        for (int head = 0; head < queue.size(); head++) {
            TrieNode node = queue.get(head);
            for (Map.Entry<Character, TrieNode> e : node.children.entrySet()) {
                char c = e.getKey();
                TrieNode child = e.getValue();
                // 逐层向下找最长可匹配后缀
                TrieNode fail = node.fail;
                while (fail != root && !fail.children.containsKey(c)) {
                    fail = fail.fail;
                }
                TrieNode candidate = fail.children.get(c);
                child.fail = (candidate != null && candidate != child) ? candidate : root;
                // 合并输出链：fail 节点结尾的词同样是本节点的命中输出
                if (child.fail.outputs != null && !child.fail.outputs.isEmpty()) {
                    if (child.outputs == null) {
                        child.outputs = new LinkedHashMap<>();
                    }
                    child.outputs.putAll(child.fail.outputs);
                }
                queue.add(child);
            }
        }
    }

    /** 命中结果：词 + 等级 */
    public static final class Hit {

        private final String word;
        private final int level;

        public Hit(String word, int level) {
            this.word = word;
            this.level = level;
        }

        public String getWord() {
            return word;
        }

        public int getLevel() {
            return level;
        }
    }
}

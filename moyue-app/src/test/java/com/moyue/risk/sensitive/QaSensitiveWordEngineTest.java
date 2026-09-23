package com.moyue.risk.sensitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveWordEngine（自研 Trie/DFA，Aho-Corasick 简化版）纯逻辑单测（P2-15 必测项）。
 *
 * <p>覆盖：PASS/REJECT/REVIEW 决策所依赖的命中语义、Aho-Corasick 失配指针正确性
 * （经典 ushers 用例）、重叠词、去重、MAX_HITS 上限、reload 热刷新（新词生效 / 删词失效）、
 * 同词多等级合并、空值边界。</p>
 */
class QaSensitiveWordEngineTest {

    private static SensitiveWordEntity word(String word, Integer level) {
        SensitiveWordEntity e = new SensitiveWordEntity();
        e.setWord(word);
        e.setLevel(level);
        return e;
    }

    private static List<String> words(List<SensitiveWordEngine.Hit> hits) {
        return hits.stream().map(SensitiveWordEngine.Hit::getWord).collect(Collectors.toList());
    }

    @Test
    @DisplayName("空文本 / null 文本不命中")
    void shouldReturnEmptyForBlankText() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("赌博", 1)));
        assertThat(engine.match(null)).isEmpty();
        assertThat(engine.match("")).isEmpty();
    }

    @Test
    @DisplayName("空词库不命中（机审放行）")
    void shouldReturnEmptyForEmptyDictionary() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of());
        assertThat(engine.match("正常内容")).isEmpty();
        assertThat(engine.size()).isZero();
    }

    @Test
    @DisplayName("reload(null) 等价于清空词库，不抛异常")
    void shouldAcceptNullReload() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(null);
        assertThat(engine.match("赌博广告")).isEmpty();
    }

    @Test
    @DisplayName("命中拦截级（level=1）与告警级（level=2）词，等级正确返回")
    void shouldMatchBlockAndWarnWords() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("赌博", SensitiveWordEngine.LEVEL_BLOCK),
                word("代充", SensitiveWordEngine.LEVEL_WARN)));
        List<SensitiveWordEngine.Hit> hits = engine.match("本站提供赌博代充服务");
        assertThat(words(hits)).containsExactlyInAnyOrder("赌博", "代充");
        assertThat(hits).extracting(SensitiveWordEngine.Hit::getLevel)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("Aho-Corasick 经典用例 ushers：she/he/hers 一次扫描全部命中")
    void shouldMatchOverlappingSuffixWords() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("she", 1), word("he", 1), word("hers", 2)));
        List<SensitiveWordEngine.Hit> hits = engine.match("ushers");
        assertThat(words(hits)).containsExactlyInAnyOrder("she", "he", "hers");
    }

    @Test
    @DisplayName("重叠词（前缀包含关系）均可命中")
    void shouldMatchPrefixOverlappingWords() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("ab", 1), word("abc", 2)));
        List<SensitiveWordEngine.Hit> hits = engine.match("xabcx");
        assertThat(words(hits)).containsExactlyInAnyOrder("ab", "abc");
    }

    @Test
    @DisplayName("同一词在文本中重复出现只计一次（去重），且按首次出现顺序")
    void shouldDeduplicateRepeatedHits() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("广告", 2), word("赌博", 1)));
        List<SensitiveWordEngine.Hit> hits = engine.match("广告...广告...赌博...广告");
        assertThat(words(hits)).containsExactly("广告", "赌博");
    }

    @Test
    @DisplayName("命中数达到上限 100：超长文本 + 巨量命中只返回前 100 个")
    void shouldCapHitsAt100() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        List<SensitiveWordEntity> dict = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            String w = "敏感词" + i;
            dict.add(word(w, 1));
            text.append(w).append('|');
        }
        engine.reload(dict);
        List<SensitiveWordEngine.Hit> hits = engine.match(text.toString());
        assertThat(hits).hasSize(100);
        // 去重后仍是 100 个不同词
        assertThat(hits.stream().map(SensitiveWordEngine.Hit::getWord).distinct().count()).isEqualTo(100);
    }

    @Test
    @DisplayName("热刷新：reload 后新增词生效、被移除词失效、size 同步")
    void reloadShouldHotSwapDictionary() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("旧词", 1)));
        assertThat(engine.match("包含旧词的内容")).hasSize(1);

        // 新增词生效
        engine.reload(List.of(word("旧词", 1), word("新词", 2)));
        assertThat(engine.match("包含新词的内容")).hasSize(1);
        assertThat(engine.match("包含旧词的内容")).hasSize(1);
        assertThat(engine.size()).isEqualTo(2);

        // 删词失效
        engine.reload(List.of(word("新词", 2)));
        assertThat(engine.match("包含旧词的内容")).isEmpty();
        assertThat(engine.match("包含新词的内容")).hasSize(1);
        assertThat(engine.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("同词重复导入不同等级：生效等级取更严的（1 拦截优先于 2 告警）")
    void shouldKeepStrictestLevelForDuplicateWord() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("违禁", 2), word("违禁", 1)));
        List<SensitiveWordEngine.Hit> hits = engine.match("违禁品");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("level 为 null 的词默认按拦截级（LEVEL_BLOCK）处理")
    void shouldDefaultNullLevelToBlock() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("炸弹", null)));
        List<SensitiveWordEngine.Hit> hits = engine.match("制作炸弹");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getLevel()).isEqualTo(SensitiveWordEngine.LEVEL_BLOCK);
    }

    @Test
    @DisplayName("空白词 / null 实体在 reload 时被忽略，词文本两端空白被 trim")
    void shouldIgnoreBlankAndTrimWords() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        // List.of 不允许 null 元素，用 ArrayList 承载含 null 实体的输入
        List<SensitiveWordEntity> dict = new ArrayList<>();
        dict.add(word("  ", 1));
        dict.add(word(null, 1));
        dict.add(null);
        dict.add(word("  广告  ", 2));
        engine.reload(dict);
        assertThat(engine.size()).isEqualTo(1);
        assertThat(words(engine.match("这是广告"))).containsExactly("广告");
    }

    @Test
    @DisplayName("大小写敏感：'Bad' 与 'bad' 为不同词（当前引擎不做大小写折叠，行为固定）")
    void shouldBeCaseSensitiveByDesign() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("bad", 1)));
        assertThat(words(engine.match("this is bad"))).containsExactly("bad");
        assertThat(engine.match("this is BAD")).isEmpty();
        assertThat(engine.match("this is Bad")).isEmpty();
    }

    @Test
    @DisplayName("不含词的文本不命中（false positive 防御）")
    void shouldNotHitOnSimilarButDifferentText() {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(List.of(word("赌博", 1)));
        assertThat(engine.match("赌 博 是两个词")).isEmpty();
        assertThat(engine.match("抽象代数")).isEmpty();
    }
}

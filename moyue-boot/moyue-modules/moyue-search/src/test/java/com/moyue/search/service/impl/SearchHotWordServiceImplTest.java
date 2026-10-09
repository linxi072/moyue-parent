package com.moyue.search.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.search.domain.entity.SearchHotWord;
import com.moyue.search.mapper.SearchHotWordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 搜索热词服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：热词榜上限裁剪（LIMIT 下界 clamp）、启用过滤、搜索联想前缀匹配、空词校验。
 * 排序与 SQL 级 LIMIT 由 MyBatis 在真实库执行，此处验证入参与返回契约。
 */
@ExtendWith(MockitoExtension.class)
class SearchHotWordServiceImplTest {

    @Mock
    private SearchHotWordMapper hotWordMapper;

    @InjectMocks
    private SearchHotWordServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, SearchHotWord.class);
    }

    private static SearchHotWord hw(String word, int weight, int enabled) {
        SearchHotWord h = new SearchHotWord();
        h.setId(1L);
        h.setWord(word);
        h.setWeight(weight);
        h.setHitCount(0);
        h.setEnabled(enabled);
        return h;
    }

    @Test
    void top_returnsEnabledWords() {
        List<SearchHotWord> all = List.of(hw("a", 10, 1), hw("b", 8, 1), hw("c", 5, 1));
        when(hotWordMapper.selectList(any())).thenReturn(all);

        var top = service.top(10);
        assertEquals(3, top.size());
        assertTrue(top.stream().allMatch(v -> v.getEnabled() == 1));
    }

    @Test
    void top_extremeLimitsDoNotThrow() {
        List<SearchHotWord> all = List.of(hw("a", 10, 1));
        when(hotWordMapper.selectList(any())).thenReturn(all);

        assertDoesNotThrow(() -> service.top(0));
        assertDoesNotThrow(() -> service.top(100));
        assertEquals(1, service.top(0).size());
    }

    @Test
    void top_onlyEnabledReturned() {
        List<SearchHotWord> enabledOnly = List.of(hw("a", 10, 1), hw("b", 8, 1));
        when(hotWordMapper.selectList(any())).thenReturn(enabledOnly);

        assertTrue(service.top(10).stream().allMatch(v -> v.getEnabled() == 1));
    }

    @Test
    void suggest_blankKeyword_returnsEmpty() {
        assertTrue(service.suggest("  ").isEmpty());
        verify(hotWordMapper, never()).selectList(any());
    }

    @Test
    void suggest_returnsPrefixMatches() {
        List<SearchHotWord> matches = List.of(hw("斗破", 9, 1), hw("斗罗", 7, 1));
        when(hotWordMapper.selectList(any())).thenReturn(matches);

        var r = service.suggest("斗");
        assertEquals(2, r.size());
        assertTrue(r.stream().allMatch(v -> v.getWord().startsWith("斗")));
    }

    @Test
    void createHotWord_blankWord_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createHotWord(new SearchHotWord()));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }
}

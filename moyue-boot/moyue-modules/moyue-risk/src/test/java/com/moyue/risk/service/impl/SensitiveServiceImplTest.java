package com.moyue.risk.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.risk.domain.entity.SensitiveWord;
import com.moyue.risk.mapper.SensitiveWordMapper;
import com.moyue.risk.service.SensitiveService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 敏感词检测单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：无启用词不命中、命中返回 true 且累加计数、拦截级优先于告警级。
 */
@ExtendWith(MockitoExtension.class)
class SensitiveServiceImplTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    @InjectMocks
    private SensitiveServiceImpl service;

    /** 纯 Mockito 测试无 Spring 上下文，需手动注册实体 TableInfo 以支持 LambdaQueryWrapper 的列解析。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, SensitiveWord.class);
    }


    private SensitiveWord word(String w, int level) {
        SensitiveWord s = new SensitiveWord();
        s.setId(1L);
        s.setWord(w);
        s.setLevel(level);
        s.setEnabled(SensitiveWord.ENABLED_ON);
        return s;
    }

    @Test
    void contains_false_when_no_enabled_words() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of());
        assertFalse(service.contains("这是一段正常文本"));
        assertTrue(service.matchLevel("正常文本").isEmpty());
        assertTrue(service.firstHit("正常文本").isEmpty());
        verify(sensitiveWordMapper, never()).update(any(), any());
    }

    @Test
    void contains_true_and_hitCount_incremented() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(word("违规", SensitiveWord.LEVEL_BLOCK)));
        when(sensitiveWordMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(service.contains("这是一段包含违规的文本"));
        assertEquals(Optional.of(SensitiveWord.LEVEL_BLOCK), service.matchLevel("这是一段包含违规的文本"));
        assertEquals(Optional.of("违规"), service.firstHit("这是一段包含违规的文本"));
        // contains / matchLevel / firstHit 各触发一次 detect，命中后各累加一次计数
        verify(sensitiveWordMapper, times(3)).update(isNull(), any());
    }

    @Test
    void matchLevel_prefers_block_over_warn() {
        when(sensitiveWordMapper.selectList(any())).thenReturn(List.of(
                word("aaa", SensitiveWord.LEVEL_BLOCK), word("bbb", SensitiveWord.LEVEL_WARN)));
        when(sensitiveWordMapper.update(isNull(), any())).thenReturn(1);

        // 文本同时包含两个词，拦截(1)应优先于告警(2)
        assertEquals(SensitiveWord.LEVEL_BLOCK, service.matchLevel("前缀aaamiddlebbb后缀").orElse(-1));
    }
}

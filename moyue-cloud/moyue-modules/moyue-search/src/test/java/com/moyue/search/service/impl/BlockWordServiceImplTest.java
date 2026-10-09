package com.moyue.search.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.search.domain.entity.BlockWord;
import com.moyue.search.mapper.BlockWordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 搜索屏蔽词服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：空词校验、唯一性去重、默认值（级别/启用）、启用/停用、不存在即 404。
 */
@ExtendWith(MockitoExtension.class)
class BlockWordServiceImplTest {

    @Mock
    private BlockWordMapper blockWordMapper;

    @InjectMocks
    private BlockWordServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, BlockWord.class);
    }

    private static BlockWord bw(String word, Integer level, int enabled) {
        BlockWord b = new BlockWord();
        b.setId(1L);
        b.setWord(word);
        b.setLevel(level);
        b.setEnabled(enabled);
        return b;
    }

    @Test
    void createBlockWord_blankWord_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createBlockWord(new BlockWord()));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void createBlockWord_duplicate_throwsParamError() {
        when(blockWordMapper.selectOne(any())).thenReturn(bw("敏感", 1, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createBlockWord(bw("敏感", 1, 1)));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void createBlockWord_defaultsAndInserts() {
        when(blockWordMapper.selectOne(any())).thenReturn(null);
        when(blockWordMapper.insert(any(BlockWord.class))).thenReturn(1);

        BlockWord bare = new BlockWord();
        bare.setWord("新词");
        service.createBlockWord(bare);

        ArgumentCaptor<BlockWord> cap = ArgumentCaptor.forClass(BlockWord.class);
        verify(blockWordMapper).insert(cap.capture());
        assertEquals(1, cap.getValue().getLevel());
        assertEquals(1, cap.getValue().getEnabled());
    }

    @Test
    void enable_notFound_throwsNotFound() {
        when(blockWordMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enable(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void disable_setsDisabled() {
        when(blockWordMapper.selectById(1L)).thenReturn(bw("x", 1, 1));
        when(blockWordMapper.updateById(any(BlockWord.class))).thenReturn(1);

        assertTrue(service.disable(1L));

        ArgumentCaptor<BlockWord> cap = ArgumentCaptor.forClass(BlockWord.class);
        verify(blockWordMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getEnabled());
    }
}

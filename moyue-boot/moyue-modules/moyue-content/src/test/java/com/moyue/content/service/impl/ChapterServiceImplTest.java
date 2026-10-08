package com.moyue.content.service.impl;

import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.content.domain.entity.Chapter;
import com.moyue.content.mapper.BookMapper;
import com.moyue.content.mapper.ChapterMapper;
import com.moyue.content.service.ChapterService;
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
 * 章节下架单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：下架 status→0（草稿）、章节不存在抛 NOT_FOUND。
 */
@ExtendWith(MockitoExtension.class)
class ChapterServiceImplTest {

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private BookMapper bookMapper;

    @InjectMocks
    private ChapterServiceImpl service;

    @Test
    void offshelf_sets_status_draft() {
        Chapter c = new Chapter();
        c.setId(1L);
        c.setStatus(1);
        when(chapterMapper.selectById(1L)).thenReturn(c);
        when(chapterMapper.updateById(any(Chapter.class))).thenReturn(1);

        assertTrue(service.offshelf(1L));
        ArgumentCaptor<Chapter> cap = ArgumentCaptor.forClass(Chapter.class);
        verify(chapterMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getStatus());
    }

    @Test
    void offshelf_notFound_throws() {
        when(chapterMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.offshelf(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}

package com.moyue.content.service.impl;

import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.content.domain.entity.Book;
import com.moyue.content.mapper.BookMapper;
import com.moyue.content.service.BookService;
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
 * 作品上下架单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：上架 status→1、下架 status→2、作品不存在抛 NOT_FOUND。
 */
@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookMapper bookMapper;

    @InjectMocks
    private BookServiceImpl service;

    @Test
    void online_sets_status_1() {
        Book b = new Book();
        b.setId(1L);
        b.setStatus(0);
        when(bookMapper.selectById(1L)).thenReturn(b);
        when(bookMapper.updateById(any(Book.class))).thenReturn(1);

        assertTrue(service.online(1L));
        ArgumentCaptor<Book> cap = ArgumentCaptor.forClass(Book.class);
        verify(bookMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getStatus());
    }

    @Test
    void offline_sets_status_2() {
        Book b = new Book();
        b.setId(1L);
        b.setStatus(1);
        when(bookMapper.selectById(1L)).thenReturn(b);
        when(bookMapper.updateById(any(Book.class))).thenReturn(1);

        assertTrue(service.offline(1L));
        ArgumentCaptor<Book> cap = ArgumentCaptor.forClass(Book.class);
        verify(bookMapper).updateById(cap.capture());
        assertEquals(2, cap.getValue().getStatus());
    }

    @Test
    void online_notFound_throws() {
        when(bookMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.online(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}

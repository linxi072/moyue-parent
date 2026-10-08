package com.moyue.social.service.impl;

import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.social.domain.entity.Comment;
import com.moyue.social.domain.entity.CommentLike;
import com.moyue.social.mapper.CommentLikeMapper;
import com.moyue.social.mapper.CommentMapper;
import com.moyue.social.service.CommentService;
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
 * 评论管理运营端单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：置顶/取消置顶、审核状态流转、运营删除、参数与存在性校验。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private CommentLikeMapper commentLikeMapper;

    @InjectMocks
    private CommentServiceImpl service;

    @Test
    void top_sets_flag() {
        Comment c = new Comment();
        c.setId(1L);
        when(commentMapper.selectById(1L)).thenReturn(c);
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        assertTrue(service.top(1L, 1));
        ArgumentCaptor<Comment> cap = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getTop());
    }

    @Test
    void top_invalid_value_throws() {
        Comment c = new Comment();
        c.setId(1L);
        when(commentMapper.selectById(1L)).thenReturn(c);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.top(1L, 5));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(commentMapper, never()).updateById(any(Comment.class));
    }

    @Test
    void audit_sets_status() {
        Comment c = new Comment();
        c.setId(1L);
        when(commentMapper.selectById(1L)).thenReturn(c);
        when(commentMapper.updateById(any(Comment.class))).thenReturn(1);

        assertTrue(service.audit(1L, 2));
        ArgumentCaptor<Comment> cap = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).updateById(cap.capture());
        assertEquals(2, cap.getValue().getStatus());
    }

    @Test
    void audit_invalid_status_throws() {
        Comment c = new Comment();
        c.setId(1L);
        when(commentMapper.selectById(1L)).thenReturn(c);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.audit(1L, 9));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void adminDelete_logical_removes() {
        Comment c = new Comment();
        c.setId(1L);
        when(commentMapper.selectById(1L)).thenReturn(c);
        when(commentMapper.deleteById(1L)).thenReturn(1);

        assertTrue(service.adminDelete(1L));
        verify(commentMapper).deleteById(1L);
    }

    @Test
    void adminDelete_notFound_throws() {
        when(commentMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.adminDelete(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }
}

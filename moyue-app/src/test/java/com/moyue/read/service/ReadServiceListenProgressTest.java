package com.moyue.read.service;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.read.dto.ListenProgressDTO;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.mapper.BookshelfMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ReadService 听书进度（P2-L）单元测试（Mockito，无 Spring 上下文）：保存更新列、读取映射、不在书架报错。
 */
@ExtendWith(MockitoExtension.class)
class ReadServiceListenProgressTest {

    @Mock
    private BookshelfMapper bookshelfMapper;

    @InjectMocks
    private ReadService readService;

    private BookshelfEntity activeRow(Long bookId) {
        BookshelfEntity e = new BookshelfEntity();
        e.setId(99L);
        e.setUserId(1L);
        e.setBookId(bookId);
        e.setIsDeleted(0);
        return e;
    }

    @Test
    void saveListenProgress_updatesColumns() {
        BookshelfEntity row = activeRow(10L);
        when(bookshelfMapper.selectOne(any())).thenReturn(row);

        readService.saveListenProgress(1L, 10L, 5L, 3, 120);

        assertThat(row.getListenChapterId()).isEqualTo(5L);
        assertThat(row.getListenSegmentIndex()).isEqualTo(3);
        assertThat(row.getListenCharOffset()).isEqualTo(120);
    }

    @Test
    void getListenProgress_mapsFields() {
        BookshelfEntity row = activeRow(10L);
        row.setListenChapterId(5L);
        row.setListenSegmentIndex(3);
        row.setListenCharOffset(120);
        when(bookshelfMapper.selectOne(any())).thenReturn(row);

        ListenProgressDTO dto = readService.getListenProgress(1L, 10L);

        assertThat(dto.getBookId()).isEqualTo(10L);
        assertThat(dto.getChapterId()).isEqualTo(5L);
        assertThat(dto.getSegmentIndex()).isEqualTo(3);
        assertThat(dto.getCharOffset()).isEqualTo(120);
    }

    @Test
    void notOnShelf_resourceNotFound() {
        when(bookshelfMapper.selectOne(any())).thenReturn(null);

        BizException ex = catchThrowableOfType(() -> readService.getListenProgress(1L, 10L), BizException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }
}

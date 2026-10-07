package com.moyue.author.center;

import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 批量章节操作纯单测（Mockito 隔离 ChapterService）。验证「整批回滚 + 失败列表」：
 * 部分失败收集 failedIds 并抛 {@link BatchOperationException}；全部成功返回 success；
 * batchUpdateStatus 仅允许 0/1（否则 PARAM_ERROR）；匿名触发 UNAUTHORIZED。
 */
class ChapterBatchOpTest {

    private final ChapterService chapterService = mock(ChapterService.class);

    private AuthorCenterService service;

    @BeforeEach
    void setUp() {
        service = new AuthorCenterService();
        ReflectionTestUtils.setField(service, "chapterService", chapterService);
    }

    // ------------------------------ 批量删除 ------------------------------

    @Test
    @DisplayName("batchDeleteChapters 部分失败：抛出 BatchOperationException 且 failedIds 正确")
    void batchDelete_partialFail_throwsBatchException() {
        doNothing().when(chapterService).deleteChapter(1L, 1L, 2);
        doThrow(new BizException(ResultCode.CONTENT_BLOCKED)).when(chapterService).deleteChapter(2L, 1L, 2);

        assertThatThrownBy(() -> service.batchDeleteChapters(1L, 2, Arrays.asList(1L, 2L)))
                .isInstanceOf(BatchOperationException.class)
                .satisfies(e -> assertThat(((BatchOperationException) e).getFailedIds()).containsExactly(2L));
    }

    @Test
    @DisplayName("batchDeleteChapters 全部成功：返回 success + publishedIds")
    void batchDelete_allSuccess() {
        doNothing().when(chapterService).deleteChapter(anyLong(), anyLong(), anyInt());

        BatchResult r = service.batchDeleteChapters(1L, 2, Arrays.asList(1L, 2L));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getPublishedIds()).containsExactly(1L, 2L);
        assertThat(r.getFailedIds()).isEmpty();
    }

    // ------------------------------ 批量发布 ------------------------------

    @Test
    @DisplayName("batchPublishChapters 部分失败（含机审 REJECT）：抛 BatchOperationException")
    void batchPublish_partialFail_throwsBatchException() {
        when(chapterService.publish(1L, 1L, 2, null)).thenReturn(new ChapterEntity());
        when(chapterService.publish(2L, 1L, 2, null)).thenThrow(new BizException(ResultCode.CONTENT_BLOCKED));

        assertThatThrownBy(() -> service.batchPublishChapters(1L, 2, Arrays.asList(1L, 2L), null))
                .isInstanceOf(BatchOperationException.class)
                .satisfies(e -> assertThat(((BatchOperationException) e).getFailedIds()).containsExactly(2L));
    }

    @Test
    @DisplayName("batchPublishChapters 全部成功：返回 success")
    void batchPublish_allSuccess() {
        when(chapterService.publish(anyLong(), anyLong(), anyInt(), any())).thenReturn(new ChapterEntity());

        BatchResult r = service.batchPublishChapters(1L, 2, Arrays.asList(1L, 2L), null);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getPublishedIds()).containsExactly(1L, 2L);
    }

    // ------------------------------ 批量改状态 ------------------------------

    @Test
    @DisplayName("batchUpdateStatus targetStatus=2：PARAM_ERROR（禁止绕过机审）")
    void batchUpdateStatus_invalidTarget() {
        assertThatThrownBy(() -> service.batchUpdateStatus(1L, 2, Arrays.asList(1L), 2))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("batchUpdateStatus 全部成功（targetStatus=1）：返回 success")
    void batchUpdateStatus_allSuccess() {
        when(chapterService.updateChapter(anyLong(), anyLong(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new ChapterEntity());

        BatchResult r = service.batchUpdateStatus(1L, 2, Arrays.asList(1L, 2L), 1);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getPublishedIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("batchUpdateStatus 部分失败：抛 BatchOperationException")
    void batchUpdateStatus_partialFail() {
        when(chapterService.updateChapter(eq(1L), anyLong(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new ChapterEntity());
        when(chapterService.updateChapter(eq(2L), anyLong(), anyInt(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException(ResultCode.FORBIDDEN));

        assertThatThrownBy(() -> service.batchUpdateStatus(1L, 2, Arrays.asList(1L, 2L), 0))
                .isInstanceOf(BatchOperationException.class)
                .satisfies(e -> assertThat(((BatchOperationException) e).getFailedIds()).containsExactly(2L));
    }

    // ------------------------------ 身份 ------------------------------

    @Test
    @DisplayName("batchDeleteChapters 匿名(userId=null)：UNAUTHORIZED")
    void batchDelete_anonymous_throwsUnauthorized() {
        assertThatThrownBy(() -> service.batchDeleteChapters(null, null, Arrays.asList(1L)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
    }
}

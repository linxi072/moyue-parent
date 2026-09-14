package com.moyue.chapter;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.mapper.ChapterMapper;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChapterService 索引同步触发链回归测试（回归 S2 / S3）：
 * 覆盖全部发布路径（正常发布 / 机审 REVIEW 转审核中 / 定时发布 / 机审拦截）与
 * 审核回写（通过 / 驳回 / 非法状态）、编辑直达发布、删除清理——断言
 * indexChapter / removeChapter 的触发与不触发边界。
 */
class ChapterServiceIndexHookTest {

    private final ChapterMapper chapterMapper = mock(ChapterMapper.class);
    private final BookClient bookClient = mock(BookClient.class);
    private final RiskClient riskClient = mock(RiskClient.class);
    private final SearchIndexClient searchIndexClient = mock(SearchIndexClient.class);

    private final ChapterService service = createService();

    private ChapterService createService() {
        ChapterService s = new ChapterService();
        ReflectionTestUtils.setField(s, "chapterMapper", chapterMapper);
        ReflectionTestUtils.setField(s, "bookClient", bookClient);
        ReflectionTestUtils.setField(s, "riskClient", riskClient);
        ReflectionTestUtils.setField(s, "searchIndexClient", searchIndexClient);
        return s;
    }

    private static final long USER_ID = 7L;
    private static final int ROLE_AUTHOR = 2;

    private ChapterEntity draft() {
        ChapterEntity e = new ChapterEntity();
        e.setId(1L);
        e.setBookId(10L);
        e.setChapterNo(1);
        e.setTitle("第一章");
        e.setContent("正文内容");
        e.setWordCount(4);
        e.setStatus(0);
        e.setIsDeleted(0);
        return e;
    }

    private void stubOwnerOk() {
        BookSummaryDTO book = new BookSummaryDTO();
        book.setAuthorId(USER_ID);
        book.setTitle("凡人修仙传");
        when(bookClient.getBook(10L)).thenReturn(R.ok(book));
    }

    private void stubModeration(String decision) {
        ModerationResultDTO result = new ModerationResultDTO();
        result.setDecision(decision);
        when(riskClient.moderate(any())).thenReturn(R.ok(result));
        when(chapterMapper.selectById(1L)).thenReturn(draft());
    }

    // ------------------------------ publish（回归发布路径全覆盖） ------------------------------

    @Test
    @DisplayName("publish 正常发布（机审 PASS）：status=2 → indexChapter 触发，载荷含解析书名")
    void publishShouldIndexChapterWhenPublished() {
        stubOwnerOk();
        stubModeration("PASS");

        ChapterEntity e = service.publish(1L, USER_ID, ROLE_AUTHOR, null);

        assertThat(e.getStatus()).isEqualTo(2);
        verify(chapterMapper).updateById(e);
        ArgumentCaptor<ChapterIndexDTO> captor = ArgumentCaptor.forClass(ChapterIndexDTO.class);
        verify(searchIndexClient).indexChapter(captor.capture());
        ChapterIndexDTO dto = captor.getValue();
        assertThat(dto.getChapterId()).isEqualTo(1L);
        assertThat(dto.getBookId()).isEqualTo(10L);
        assertThat(dto.getBookTitle()).isEqualTo("凡人修仙传");
        assertThat(dto.getChapterTitle()).isEqualTo("第一章");
        assertThat(dto.getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("publish 机审 REVIEW（转人工）：status=1，不入索引")
    void publishShouldNotIndexWhenReview() {
        stubOwnerOk();
        stubModeration("REVIEW");

        ChapterEntity e = service.publish(1L, USER_ID, ROLE_AUTHOR, null);

        assertThat(e.getStatus()).isEqualTo(1);
        verify(searchIndexClient, never()).indexChapter(any());
    }

    @Test
    @DisplayName("publish 定时发布（未来时间）：status=1 审核中，不入索引")
    void publishShouldNotIndexWhenScheduled() {
        stubOwnerOk();
        stubModeration("PASS");

        ChapterEntity e = service.publish(1L, USER_ID, ROLE_AUTHOR, LocalDateTime.now().plusDays(1));

        assertThat(e.getStatus()).isEqualTo(1);
        verify(searchIndexClient, never()).indexChapter(any());
    }

    @Test
    @DisplayName("publish 机审 REJECT：抛 CONTENT_BLOCKED，不落库不入索引")
    void publishShouldRejectAndNotIndexWhenBlocked() {
        stubOwnerOk();
        stubModeration("REJECT");

        assertThatThrownBy(() -> service.publish(1L, USER_ID, ROLE_AUTHOR, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.CONTENT_BLOCKED.getCode());
        verify(chapterMapper, never()).updateById(any(ChapterEntity.class));
        verify(searchIndexClient, never()).indexChapter(any());
    }

    @Test
    @DisplayName("publish 超长正文：索引载荷正文截断至 20000 字符")
    void publishShouldTruncateContentForIndex() {
        stubOwnerOk();
        stubModeration("PASS");
        ChapterEntity e = draft();
        e.setContent("长".repeat(20001));
        when(chapterMapper.selectById(1L)).thenReturn(e);

        service.publish(1L, USER_ID, ROLE_AUTHOR, null);

        ArgumentCaptor<ChapterIndexDTO> captor = ArgumentCaptor.forClass(ChapterIndexDTO.class);
        verify(searchIndexClient).indexChapter(captor.capture());
        assertThat(captor.getValue().getContent()).hasSize(20000);
    }

    @Test
    @DisplayName("publish 时索引推送失败：仅 warn 不阻断发布主流程")
    void publishShouldNotBlockWhenIndexPushFails() {
        stubOwnerOk();
        stubModeration("PASS");
        doThrow(new RuntimeException("connection refused"))
                .when(searchIndexClient).indexChapter(any());

        assertThatCode(() -> service.publish(1L, USER_ID, ROLE_AUTHOR, null))
                .doesNotThrowAnyException();
        verify(chapterMapper).updateById(any(ChapterEntity.class));
    }

    // ------------------------------ auditChapter（回归 S2） ------------------------------

    @Test
    @DisplayName("回归 S2：审核回写通过（status=2）→ indexChapter 立即触发")
    void auditShouldIndexWhenPublished() {
        stubModeration("PASS");
        ChapterEntity e = draft();
        when(chapterMapper.selectById(1L)).thenReturn(e);

        service.auditChapter(1L, 2);

        verify(chapterMapper).updateById(e);
        verify(searchIndexClient).indexChapter(any(ChapterIndexDTO.class));
    }

    @Test
    @DisplayName("审核回写驳回（status=3）：不入索引")
    void auditShouldNotIndexWhenRejected() {
        ChapterEntity e = draft();
        when(chapterMapper.selectById(1L)).thenReturn(e);

        service.auditChapter(1L, 3);

        verify(chapterMapper).updateById(e);
        verify(searchIndexClient, never()).indexChapter(any());
    }

    @Test
    @DisplayName("审核回写非法状态：PARAM_ERROR，不动库不入索引")
    void auditShouldRejectInvalidStatus() {
        assertThatThrownBy(() -> service.auditChapter(1L, 9)).isInstanceOf(BizException.class);
        verify(searchIndexClient, never()).indexChapter(any());
    }

    // ------------------------------ updateChapter / deleteChapter（回归 S3） ------------------------------

    @Test
    @DisplayName("编辑直达发布（status 传 2）→ indexChapter 触发")
    void updateShouldIndexWhenEditToPublished() {
        stubOwnerOk();
        ChapterEntity e = draft();
        when(chapterMapper.selectById(1L)).thenReturn(e);

        service.updateChapter(1L, USER_ID, ROLE_AUTHOR, "新标题", null, null, 2, null);

        verify(chapterMapper).updateById(e);
        verify(searchIndexClient).indexChapter(any(ChapterIndexDTO.class));
    }

    @Test
    @DisplayName("回归 S3：deleteChapter → removeChapter 同步清理 ES 索引文档")
    void deleteShouldRemoveChapterIndex() {
        stubOwnerOk();
        ChapterEntity e = draft();
        when(chapterMapper.selectById(1L)).thenReturn(e);

        service.deleteChapter(1L, USER_ID, ROLE_AUTHOR);

        verify(chapterMapper).deleteById(1L);
        verify(searchIndexClient).removeChapter(1L);
    }

    @Test
    @DisplayName("deleteChapter 索引清理失败：仅 warn，删除主流程不受影响")
    void deleteShouldNotBlockWhenIndexRemovalFails() {
        stubOwnerOk();
        ChapterEntity e = draft();
        when(chapterMapper.selectById(1L)).thenReturn(e);
        doThrow(new RuntimeException("connection refused"))
                .when(searchIndexClient).removeChapter(1L);

        assertThatCode(() -> service.deleteChapter(1L, USER_ID, ROLE_AUTHOR))
                .doesNotThrowAnyException();
        verify(chapterMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteChapter 章节不存在：RESOURCE_NOT_FOUND，不触达索引客户端")
    void deleteShouldFailFastWhenChapterMissing() {
        when(chapterMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteChapter(1L, USER_ID, ROLE_AUTHOR))
                .isInstanceOf(BizException.class);
        verify(searchIndexClient, never()).removeChapter(1L);
    }
}

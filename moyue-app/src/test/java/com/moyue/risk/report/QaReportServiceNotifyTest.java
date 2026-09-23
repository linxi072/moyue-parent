package com.moyue.risk.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.client.ChapterClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.api.message.client.MessageDispatchClient;
import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.social.client.CommentClient;
import com.moyue.api.social.dto.CommentDTO;
import com.moyue.common.R;
import com.moyue.risk.report.ReportEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportService 处理举报后「被处理方通知」闭环纯 Mockito 单测（不启 Spring / 不依赖 Docker）。
 * 覆盖：书籍/章节/评论/用户四类举报对象的归属人解析与 OWNER_NOTICE 站内信通知、
 * 以及触达服务不可用降级（通知失败不回滚举报处理结果）。
 */
class QaReportServiceNotifyTest {

    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ReportEntity.class);
    }

    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final ChapterClient chapterClient = mock(ChapterClient.class);
    private final CommentClient commentClient = mock(CommentClient.class);
    private final BookClient bookClient = mock(BookClient.class);
    private final MessageDispatchClient messageDispatchClient = mock(MessageDispatchClient.class);

    private ReportService service() {
        ReportService s = new ReportService();
        ReflectionTestUtils.setField(s, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(s, "chapterClient", chapterClient);
        ReflectionTestUtils.setField(s, "commentClient", commentClient);
        ReflectionTestUtils.setField(s, "bookClient", bookClient);
        ReflectionTestUtils.setField(s, "messageDispatchClient", messageDispatchClient);
        return s;
    }

    private ReportEntity report(long id, int targetType, long targetId, int status) {
        ReportEntity e = new ReportEntity();
        e.setId(id);
        e.setReporterId(99L);
        e.setTargetType(targetType);
        e.setTargetId(targetId);
        e.setStatus(status);
        e.setIsDeleted(0);
        return e;
    }

    private MessageDispatchDTO captureOwnerNotice() {
        ArgumentCaptor<MessageDispatchDTO> cap = ArgumentCaptor.forClass(MessageDispatchDTO.class);
        verify(messageDispatchClient, times(2)).dispatch(cap.capture());
        return cap.getAllValues().stream()
                .filter(d -> "OWNER_NOTICE".equals(d.getTemplateCode()))
                .findFirst().orElse(null);
    }

    // ------------------------------ 四类对象归属人通知 ------------------------------

    @Test
    @DisplayName("举报评论属实：OWNER_NOTICE 站内信通知评论者，含 targetDesc/result/reason")
    void handle_commentConfirmed_notifiesCommenter() {
        ReportEntity r = report(1L, 3, 3001L, 0);
        when(reportMapper.selectOne(any())).thenReturn(r);
        when(commentClient.auditComment(3001L, 2)).thenReturn(R.ok());
        CommentDTO comment = new CommentDTO();
        comment.setUserId(7L);
        when(commentClient.getComment(3001L)).thenReturn(R.ok(comment));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().handle(1L, true, "内容违规已处理", 3L);

        MessageDispatchDTO dto = captureOwnerNotice();
        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo(7L);
        assertThat(dto.getChannels()).containsExactly(1);
        assertThat(dto.getBizType()).isEqualTo("REPORT");
        assertThat(dto.getBizId()).isEqualTo(1L);
        assertThat(dto.getParams()).containsEntry("targetDesc", "评论#3001");
        assertThat(dto.getParams()).containsEntry("result", "属实，已下架/隐藏");
        assertThat(dto.getParams()).containsEntry("reason", "内容违规已处理");
    }

    @Test
    @DisplayName("举报书籍属实：OWNER_NOTICE 通知书籍作者")
    void handle_bookConfirmed_notifiesAuthor() {
        ReportEntity r = report(2L, 1, 1001L, 0);
        when(reportMapper.selectOne(any())).thenReturn(r);
        BookSummaryDTO book = new BookSummaryDTO();
        book.setAuthorId(42L);
        when(bookClient.getBook(1001L)).thenReturn(R.ok(book));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().handle(2L, true, "侵权已下架", 3L);

        MessageDispatchDTO dto = captureOwnerNotice();
        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo(42L);
        assertThat(dto.getParams()).containsEntry("targetDesc", "书籍#1001");
    }

    @Test
    @DisplayName("举报章节属实：经 chapter→book 解析作者，OWNER_NOTICE 通知")
    void handle_chapterConfirmed_notifiesAuthor() {
        ReportEntity r = report(3L, 2, 2001L, 0);
        when(reportMapper.selectOne(any())).thenReturn(r);
        when(chapterClient.auditChapter(2001L, 3)).thenReturn(R.ok());
        ChapterDTO chapter = new ChapterDTO();
        chapter.setBookId(1001L);
        when(chapterClient.getChapter(2001L)).thenReturn(R.ok(chapter));
        BookSummaryDTO book = new BookSummaryDTO();
        book.setAuthorId(42L);
        when(bookClient.getBook(1001L)).thenReturn(R.ok(book));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().handle(3L, true, "违规章节已下架", 3L);

        MessageDispatchDTO dto = captureOwnerNotice();
        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo(42L);
        assertThat(dto.getParams()).containsEntry("targetDesc", "章节#2001");
    }

    @Test
    @DisplayName("举报用户：OWNER_NOTICE 直接通知被举报用户自身（targetId=userId）")
    void handle_userConfirmed_notifiesUserItself() {
        ReportEntity r = report(4L, 4, 555L, 0);
        when(reportMapper.selectOne(any())).thenReturn(r);
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().handle(4L, true, "账号违规已处理", 3L);

        MessageDispatchDTO dto = captureOwnerNotice();
        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo(555L);
        assertThat(dto.getParams()).containsEntry("targetDesc", "用户#555");
    }

    @Test
    @DisplayName("举报驳回：OWNER_NOTICE 结果文案为「驳回（举报不成立）」")
    void handle_rejected_ownerNoticeSaysRejected() {
        ReportEntity r = report(5L, 3, 3002L, 0);
        when(reportMapper.selectOne(any())).thenReturn(r);
        CommentDTO comment = new CommentDTO();
        comment.setUserId(7L);
        when(commentClient.getComment(3002L)).thenReturn(R.ok(comment));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().handle(5L, false, null, 3L);

        MessageDispatchDTO dto = captureOwnerNotice();
        assertThat(dto).isNotNull();
        assertThat(dto.getParams()).containsEntry("result", "驳回（举报不成立）");
        assertThat(dto.getParams()).containsEntry("reason", "经平台审核不属实");
    }

    // ------------------------------ 降级 ------------------------------

    @Test
    @DisplayName("触达服务不可用：REPORT_RESULT 与 OWNER_NOTICE 均跳过，举报结果仍落库")
    void handle_dispatchClientNull_skipsBothNoticesButPersists() {
        ReportEntity r = report(6L, 3, 3003L, 0);
        when(reportMapper.selectOne(any())).thenReturn(r);
        CommentDTO comment = new CommentDTO();
        comment.setUserId(7L);
        when(commentClient.getComment(3003L)).thenReturn(R.ok(comment));
        when(commentClient.auditComment(3003L, 2)).thenReturn(R.ok());

        ReportService s = service();
        ReflectionTestUtils.setField(s, "messageDispatchClient", null);

        s.handle(6L, true, "x", 3L);

        verify(messageDispatchClient, never()).dispatch(any());
        verify(reportMapper).updateById(any(ReportEntity.class));
    }
}

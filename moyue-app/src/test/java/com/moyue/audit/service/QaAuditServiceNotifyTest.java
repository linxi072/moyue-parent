package com.moyue.audit.service;

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
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.common.R;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditService 裁决后「被处理方通知」闭环纯 Mockito 单测（不启 Spring / 不依赖 Docker）。
 * 覆盖：章节（chapter→book→作者）与评论（评论者）两类归属人通知、模板与渠道正确、
 * 以及触达服务不可用 / 归属人解析失败 / 触达失败等降级场景（通知失败不回滚裁决）。
 */
class QaAuditServiceNotifyTest {

    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        // decide() 走 BaseMapper.selectById/updateById，与 lambda 缓存无关；预热仅为兼容既有约定
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditTaskEntity.class);
    }

    private final AuditTaskMapper auditTaskMapper = mock(AuditTaskMapper.class);
    private final ChapterClient chapterClient = mock(ChapterClient.class);
    private final CommentClient commentClient = mock(CommentClient.class);
    private final BookClient bookClient = mock(BookClient.class);
    private final MessageDispatchClient messageDispatchClient = mock(MessageDispatchClient.class);

    private AuditService service() {
        AuditService s = new AuditService();
        ReflectionTestUtils.setField(s, "auditTaskMapper", auditTaskMapper);
        ReflectionTestUtils.setField(s, "chapterClient", chapterClient);
        ReflectionTestUtils.setField(s, "commentClient", commentClient);
        ReflectionTestUtils.setField(s, "bookClient", bookClient);
        ReflectionTestUtils.setField(s, "messageDispatchClient", messageDispatchClient);
        return s;
    }

    private AuditTaskEntity chapterTask(long taskId, long chapterId) {
        AuditTaskEntity t = new AuditTaskEntity();
        t.setId(taskId);
        t.setBizType(1);
        t.setBizId(chapterId);
        t.setStatus(0);
        return t;
    }

    private AuditTaskEntity commentTask(long taskId, long commentId) {
        AuditTaskEntity t = new AuditTaskEntity();
        t.setId(taskId);
        t.setBizType(2);
        t.setBizId(commentId);
        t.setStatus(0);
        return t;
    }

    // ------------------------------ 章节裁决通知作者 ------------------------------

    @Test
    @DisplayName("章节审核通过：经 chapter→book 解析作者，按 AUDIT_PASS 站内信通知归属人")
    void decide_chapterPass_notifiesAuthorViaInbox() {
        AuditTaskEntity task = chapterTask(1L, 2001L);
        when(auditTaskMapper.selectById(1L)).thenReturn(task);
        when(chapterClient.auditChapter(2001L, 2)).thenReturn(R.ok());
        ChapterDTO chapter = new ChapterDTO();
        chapter.setBookId(1001L);
        chapter.setTitle("第一章 觉醒");
        when(chapterClient.getChapter(2001L)).thenReturn(R.ok(chapter));
        BookSummaryDTO book = new BookSummaryDTO();
        book.setAuthorId(42L);
        when(bookClient.getBook(1001L)).thenReturn(R.ok(book));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().decide(1L, true, "文笔流畅", 3L);

        ArgumentCaptor<MessageDispatchDTO> cap = ArgumentCaptor.forClass(MessageDispatchDTO.class);
        verify(messageDispatchClient).dispatch(cap.capture());
        MessageDispatchDTO dto = cap.getValue();
        assertThat(dto.getUserId()).isEqualTo(42L);
        assertThat(dto.getTemplateCode()).isEqualTo("AUDIT_PASS");
        assertThat(dto.getChannels()).containsExactly(1); // 强制站内信
        assertThat(dto.getBizType()).isEqualTo("AUDIT");
        assertThat(dto.getBizId()).isEqualTo(2001L);
        assertThat(dto.getParams()).containsEntry("bizName", "章节《第一章 觉醒》");
        assertThat(dto.getParams()).containsEntry("reason", "文笔流畅");
    }

    @Test
    @DisplayName("章节审核驳回：按 AUDIT_REJECT 通知，remark 为空时回退默认原因")
    void decide_chapterReject_notifiesAuthorWithDefaultReason() {
        AuditTaskEntity task = chapterTask(2L, 2002L);
        when(auditTaskMapper.selectById(2L)).thenReturn(task);
        when(chapterClient.auditChapter(2002L, 3)).thenReturn(R.ok());
        ChapterDTO chapter = new ChapterDTO();
        chapter.setBookId(1001L);
        chapter.setTitle("第二章 试炼");
        when(chapterClient.getChapter(2002L)).thenReturn(R.ok(chapter));
        BookSummaryDTO book = new BookSummaryDTO();
        book.setAuthorId(42L);
        when(bookClient.getBook(1001L)).thenReturn(R.ok(book));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().decide(2L, false, null, 3L);

        ArgumentCaptor<MessageDispatchDTO> cap = ArgumentCaptor.forClass(MessageDispatchDTO.class);
        verify(messageDispatchClient).dispatch(cap.capture());
        assertThat(cap.getValue().getTemplateCode()).isEqualTo("AUDIT_REJECT");
        assertThat(cap.getValue().getParams()).containsEntry("reason", "违反平台内容规范");
    }

    // ------------------------------ 评论裁决通知评论者 ------------------------------

    @Test
    @DisplayName("评论审核驳回：按 AUDIT_REJECT 站内信通知评论者 userId")
    void decide_commentReject_notifiesCommenter() {
        AuditTaskEntity task = commentTask(3L, 3001L);
        when(auditTaskMapper.selectById(3L)).thenReturn(task);
        when(commentClient.auditComment(3001L, 2)).thenReturn(R.ok());
        CommentDTO comment = new CommentDTO();
        comment.setUserId(7L);
        when(commentClient.getComment(3001L)).thenReturn(R.ok(comment));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.ok());

        service().decide(3L, false, "含违规内容", 3L);

        ArgumentCaptor<MessageDispatchDTO> cap = ArgumentCaptor.forClass(MessageDispatchDTO.class);
        verify(messageDispatchClient).dispatch(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(7L);
        assertThat(cap.getValue().getTemplateCode()).isEqualTo("AUDIT_REJECT");
        assertThat(cap.getValue().getParams()).containsEntry("bizName", "评论#3001");
    }

    // ------------------------------ 降级场景（通知失败不回滚裁决） ------------------------------

    @Test
    @DisplayName("触达服务不可用：owner 通知跳过，但裁决仍正常落库")
    void decide_dispatchClientNull_skipsNotifyButStillDecides() {
        AuditTaskEntity task = commentTask(4L, 3002L);
        when(auditTaskMapper.selectById(4L)).thenReturn(task);
        when(commentClient.auditComment(3002L, 2)).thenReturn(R.ok());

        AuditService s = service();
        ReflectionTestUtils.setField(s, "messageDispatchClient", null);

        s.decide(4L, false, "x", 3L);

        verify(messageDispatchClient, never()).dispatch(any());
        verify(auditTaskMapper).updateById(any(AuditTaskEntity.class));
    }

    @Test
    @DisplayName("章节归属人解析失败（book 无作者）：跳过通知，不抛异常、不回滚")
    void decide_chapterOwnerUnresolved_skipsNotifyGracefully() {
        AuditTaskEntity task = chapterTask(5L, 2003L);
        when(auditTaskMapper.selectById(5L)).thenReturn(task);
        when(chapterClient.auditChapter(2003L, 2)).thenReturn(R.ok());
        ChapterDTO chapter = new ChapterDTO();
        chapter.setBookId(1001L);
        chapter.setTitle("X");
        when(chapterClient.getChapter(2003L)).thenReturn(R.ok(chapter));
        BookSummaryDTO book = new BookSummaryDTO();
        book.setAuthorId(null); // 解析不到作者
        when(bookClient.getBook(1001L)).thenReturn(R.ok(book));

        service().decide(5L, true, "ok", 3L);

        verify(messageDispatchClient, never()).dispatch(any());
    }

    @Test
    @DisplayName("触达返回非成功码：仅告警忽略，裁决已落库不回滚")
    void decide_dispatchFails_doesNotRollbackDecision() {
        AuditTaskEntity task = commentTask(6L, 3003L);
        when(auditTaskMapper.selectById(6L)).thenReturn(task);
        when(commentClient.auditComment(3003L, 2)).thenReturn(R.ok());
        CommentDTO comment = new CommentDTO();
        comment.setUserId(7L);
        when(commentClient.getComment(3003L)).thenReturn(R.ok(comment));
        when(messageDispatchClient.dispatch(any())).thenReturn(R.fail(50000, "inbox down"));

        service().decide(6L, false, "x", 3L);

        verify(messageDispatchClient).dispatch(any());
        verify(auditTaskMapper).updateById(any(AuditTaskEntity.class));
    }
}

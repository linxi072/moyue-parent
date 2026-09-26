package com.moyue.audit.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.api.book.client.ReviewClient;
import com.moyue.api.message.client.MessageDispatchClient;
import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.book.review.dto.ReviewDTO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditService BIZ_REVIEW(bizType=5) 分支纯 Mockito 单测（沿用 QaAuditServiceNotifyTest 范式，不启 Spring）。
 * 覆盖：审核裁决 decide() 对书评业务走 ReviewClient.auditReview 回写，
 * 通过 → auditReview(bizId, 1)，驳回 → auditReview(bizId, 2)。同时校验 owner 通知（书评作者）闭环。
 */
class ReviewAuditServiceTest {

    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditTaskEntity.class);
    }

    private final AuditTaskMapper auditTaskMapper = mock(AuditTaskMapper.class);
    private final ReviewClient reviewClient = mock(ReviewClient.class);
    private final MessageDispatchClient messageDispatchClient = mock(MessageDispatchClient.class);

    private AuditService service() {
        AuditService s = new AuditService();
        ReflectionTestUtils.setField(s, "auditTaskMapper", auditTaskMapper);
        ReflectionTestUtils.setField(s, "reviewClient", reviewClient);
        ReflectionTestUtils.setField(s, "messageDispatchClient", messageDispatchClient);
        return s;
    }

    private AuditTaskEntity reviewTask(long taskId, long bizId) {
        AuditTaskEntity t = new AuditTaskEntity();
        t.setId(taskId);
        t.setBizType(5); // BIZ_REVIEW
        t.setBizId(bizId);
        t.setStatus(0);  // 待投递
        return t;
    }

    private void stubReviewOwner(long bizId, long userId) {
        ReviewDTO review = new ReviewDTO();
        review.setUserId(userId);
        when(reviewClient.getReview(bizId)).thenReturn(R.ok(review));
        when(messageDispatchClient.dispatch(any(MessageDispatchDTO.class))).thenReturn(R.ok());
    }

    @Test
    @DisplayName("书评审核通过：走 ReviewClient.auditReview(bizId, 1)")
    void decide_reviewPass_callsAuditReviewWithStatus1() {
        AuditTaskEntity task = reviewTask(1L, 5001L);
        when(auditTaskMapper.selectById(1L)).thenReturn(task);
        when(reviewClient.auditReview(5001L, 1)).thenReturn(R.ok());
        stubReviewOwner(5001L, 7L);

        service().decide(1L, true, "ok", 3L);

        verify(reviewClient).auditReview(5001L, 1);
        verify(auditTaskMapper).updateById(any(AuditTaskEntity.class));
    }

    @Test
    @DisplayName("书评审核驳回：走 ReviewClient.auditReview(bizId, 2)")
    void decide_reviewReject_callsAuditReviewWithStatus2() {
        AuditTaskEntity task = reviewTask(2L, 5002L);
        when(auditTaskMapper.selectById(2L)).thenReturn(task);
        when(reviewClient.auditReview(5002L, 2)).thenReturn(R.ok());
        stubReviewOwner(5002L, 7L);

        service().decide(2L, false, "违规内容", 3L);

        verify(reviewClient).auditReview(5002L, 2);
    }

    @Test
    @DisplayName("书评审核通过：owner 通知按 AUDIT_PASS 站内信触达书评作者")
    void decide_reviewPass_notifiesAuthorViaInbox() {
        AuditTaskEntity task = reviewTask(3L, 5003L);
        when(auditTaskMapper.selectById(3L)).thenReturn(task);
        when(reviewClient.auditReview(5003L, 1)).thenReturn(R.ok());
        stubReviewOwner(5003L, 7L);

        service().decide(3L, true, "文笔流畅", 3L);

        ArgumentCaptor<MessageDispatchDTO> cap = ArgumentCaptor.forClass(MessageDispatchDTO.class);
        verify(messageDispatchClient).dispatch(cap.capture());
        assertThat(cap.getValue().getUserId()).isEqualTo(7L);
        assertThat(cap.getValue().getTemplateCode()).isEqualTo("AUDIT_PASS");
    }
}

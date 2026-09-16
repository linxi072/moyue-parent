package com.moyue.ai;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.mapper.AiMessageMapper;
import com.moyue.ai.mapper.AiSessionMapper;
import com.moyue.ai.engine.AiReplyEngine;
import com.moyue.ai.engine.ReplyContext;
import com.moyue.ai.engine.ReplyResult;
import com.moyue.ai.service.AiService;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.core.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AiService.pageQaForIndex 配对逻辑单测（Mockito 隔离 MyBatis-Plus Mapper）：
 * 一轮对话一条文档（role=1 用户提问 → role=2 助手回复配对），
 * 覆盖正常配对 / 页尾悬空用户消息 / 页首悬空助手回复 / null 角色容错 / 空页。
 */
class AiServicePageQaForIndexTest {

    private final AiSessionMapper sessionMapper = mock(AiSessionMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final AiReplyEngine replyEngine = mock(AiReplyEngine.class);

    private final AiService service = createService();

    private AiService createService() {
        AiService s = new AiService();
        ReflectionTestUtils.setField(s, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(s, "messageMapper", messageMapper);
        when(replyEngine.reply(any(ReplyContext.class))).thenReturn(new ReplyResult("这是回复", true, "mock"));
        ReflectionTestUtils.setField(s, "replyEngine", replyEngine);
        // searchIndexClient 不注入：分页拉取与该客户端无关
        return s;
    }

    private static AiMessageEntity msg(long id, Long sessionId, int role, String content) {
        AiMessageEntity m = new AiMessageEntity();
        m.setId(id);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setCreateTime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(id));
        return m;
    }

    /** 模拟 MyBatis-Plus 分页：将给定消息写入 Page 后返回（按 id 升序由实现保证） */
    @SuppressWarnings("unchecked")
    private void stubPage(List<AiMessageEntity> records, long total) {
        when(messageMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenAnswer(inv -> {
                    Page<AiMessageEntity> p = inv.getArgument(0);
                    p.setRecords(records);
                    p.setTotal(total);
                    return p;
                });
    }

    @Test
    @DisplayName("正常配对：Q,A,Q,A → 两条文档，question/answer/messageId 正确")
    void pageQaForIndexShouldPairAdjacentRoles() {
        stubPage(List.of(
                msg(1, 10L, 1, "怎么充值"),
                msg(2, 10L, 2, "在钱包页操作"),
                msg(3, 10L, 1, "退款多久到账"),
                msg(4, 10L, 2, "3 个工作日")), 4L);

        PageResult<QaIndexDTO> result = service.pageQaForIndex(1, 100);

        assertThat(result.getRecords()).hasSize(2);
        QaIndexDTO first = result.getRecords().get(0);
        assertThat(first.getMessageId()).isEqualTo(2L); // 取助手回复消息 ID
        assertThat(first.getSessionId()).isEqualTo(10L);
        assertThat(first.getQuestion()).isEqualTo("怎么充值");
        assertThat(first.getAnswer()).isEqualTo("在钱包页操作");
        assertThat(first.getCreateTime()).isEqualTo(
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(2));
        QaIndexDTO second = result.getRecords().get(1);
        assertThat(second.getMessageId()).isEqualTo(4L);
        assertThat(second.getQuestion()).isEqualTo("退款多久到账");
        assertThat(second.getAnswer()).isEqualTo("3 个工作日");
    }

    @Test
    @DisplayName("页尾悬空用户消息：不产出文档（跨页由下一页按实现口径补齐）")
    void pageQaForIndexShouldNotEmitDocForDanglingQuestion() {
        stubPage(List.of(
                msg(1, 10L, 1, "怎么充值"),
                msg(2, 10L, 2, "在钱包页操作"),
                msg(3, 10L, 1, "退款多久到账")), 3L);

        PageResult<QaIndexDTO> result = service.pageQaForIndex(1, 100);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getQuestion()).isEqualTo("怎么充值");
    }

    @Test
    @DisplayName("页首悬空助手回复（上一页提问在本页作答）：产出文档但 question 为空")
    void pageQaForIndexShouldEmitDocWithEmptyQuestionForDanglingAnswer() {
        stubPage(List.of(
                msg(1, 10L, 2, "3 个工作日"),
                msg(2, 10L, 1, "怎么充值"),
                msg(3, 10L, 2, "在钱包页操作")), 3L);

        PageResult<QaIndexDTO> result = service.pageQaForIndex(1, 100);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getQuestion()).isEmpty();
        assertThat(result.getRecords().get(0).getAnswer()).isEqualTo("3 个工作日");
        assertThat(result.getRecords().get(1).getQuestion()).isEqualTo("怎么充值");
    }

    @Test
    @DisplayName("role 为 null / 非法值的消息被忽略，不产出脏文档")
    void pageQaForIndexShouldSkipUnknownRoles() {
        stubPage(List.of(
                msg(1, 10L, 0, "系统消息"),
                msg(2, 10L, 1, "怎么充值"),
                msg(3, 10L, 2, "在钱包页操作")), 3L);

        PageResult<QaIndexDTO> result = service.pageQaForIndex(1, 100);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getQuestion()).isEqualTo("怎么充值");
    }

    @Test
    @DisplayName("空页：返回空 records，total 透传")
    void pageQaForIndexShouldReturnEmptyOnBlankPage() {
        stubPage(new ArrayList<>(), 0L);

        PageResult<QaIndexDTO> result = service.pageQaForIndex(1, 100);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("分页字段透传：total / page / size 与 Mapper 分页一致")
    void pageQaForIndexShouldCarryPagingMeta() {
        stubPage(List.of(
                msg(1, 10L, 1, "q"),
                msg(2, 10L, 2, "a")), 999L);

        PageResult<QaIndexDTO> result = service.pageQaForIndex(3, 50);

        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(50);
        assertThat(result.getTotal()).isEqualTo(999L);
    }
}

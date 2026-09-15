package com.moyue.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.message.dto.MessageDispatchResultDTO;
import com.moyue.message.channel.ChannelMessage;
import com.moyue.message.channel.ChannelSendResult;
import com.moyue.message.channel.ChannelSender;
import com.moyue.message.channel.MessageChannel;
import com.moyue.message.entity.MessageChannelRecordEntity;
import com.moyue.message.mapper.MessageChannelRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MessageDispatcher 分发链路回归测试（P2-14 渠道 SPI 核心）。
 *
 * <p>纯 Mockito 单测：不启动 Spring 容器，channelSenders / 模板服务 / 记录 Mapper 全部以
 * Mock 注入，重点验证「逐渠道隔离」「渠道路由正确性」「未知渠道降级」「结果状态流转」。</p>
 */
class MessageDispatcherTest {

    /**
     * 预热 MyBatis-Plus 的 lambda 列名缓存。
     * 本测试不启动 Spring、TableInfo 从未被 MyBatis 初始化；虽然分发器本身未用 lambda 查询，
     * 但为防御后续演进（如记录侧引入条件更新）并保持与团队同款解法一致，预先注册实体表信息。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MessageChannelRecordEntity.class);
    }

    private static final long USER_ID = 7L;

    private MessageTemplateService messageTemplateService;
    private MessageChannelRecordMapper recordMapper;

    private ChannelSender inboxSender;
    private ChannelSender emailSender;
    private ChannelSender smsSender;
    private ChannelSender pushSender;

    private MessageDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        messageTemplateService = mock(MessageTemplateService.class);
        recordMapper = mock(MessageChannelRecordMapper.class);

        inboxSender = mockSender(MessageChannel.INBOX);
        emailSender = mockSender(MessageChannel.EMAIL);
        smsSender = mockSender(MessageChannel.SMS);
        pushSender = mockSender(MessageChannel.PUSH);

        dispatcher = new MessageDispatcher();
        ReflectionTestUtils.setField(dispatcher, "messageTemplateService", messageTemplateService);
        ReflectionTestUtils.setField(dispatcher, "messageChannelRecordMapper", recordMapper);
    }

    /** 按渠道造一个只返回指定 channel 的 Mock 发送器 */
    private ChannelSender mockSender(MessageChannel channel) {
        ChannelSender sender = mock(ChannelSender.class);
        when(sender.channel()).thenReturn(channel);
        return sender;
    }

    private void wireSenders(ChannelSender... senders) {
        ReflectionTestUtils.setField(dispatcher, "channelSenders", List.of(senders));
    }

    private void stubTemplate(String code, String title, String content, List<Integer> channels) {
        when(messageTemplateService.render(anyString(), any()))
                .thenReturn(new MessageTemplateService.Template(code, title, content, channels));
    }

    private MessageDispatchDTO dto(List<Integer> channels) {
        MessageDispatchDTO dto = new MessageDispatchDTO();
        dto.setUserId(USER_ID);
        dto.setTemplateCode("AUDIT_PASS");
        dto.setParams(Map.of("book", "凡人修仙传"));
        dto.setChannels(channels);
        dto.setBizType("AUDIT");
        dto.setBizId(99L);
        return dto;
    }

    // ------------------------------ 逐渠道隔离 ------------------------------

    @Test
    @DisplayName("逐渠道隔离：EMAIL 发送抛异常时，其它渠道仍投递成功且整体不抛异常")
    void dispatchShouldIsolateFailingChannel() {
        wireSenders(inboxSender, emailSender, smsSender, pushSender);
        stubTemplate("AUDIT_PASS", "审核通过", "您的作品已通过", List.of(1, 2, 3, 4));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        when(emailSender.send(any())).thenThrow(new RuntimeException("boom"));
        when(smsSender.send(any())).thenReturn(ChannelSendResult.pending("sms-stub"));
        when(pushSender.send(any())).thenReturn(ChannelSendResult.pending("push-stub"));

        MessageDispatchResultDTO result = dispatcher.dispatch(dto(List.of(1, 2, 3, 4)));

        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getChannelDetails()).containsExactly(
                "INBOX=OK",
                "EMAIL=FAILED 渠道异常：boom",
                "SMS=PENDING sms-stub",
                "PUSH=PENDING push-stub");
        // EMAIL 在序列中间失败，其后的 SMS / PUSH 仍被投递 —— 证明逐渠道隔离生效
        verify(inboxSender).send(any());
        verify(emailSender).send(any());
        verify(smsSender).send(any());
        verify(pushSender).send(any());
    }

    @Test
    @DisplayName("逐渠道隔离：整批分发不因单渠道异常向上抛出")
    void dispatchShouldNotPropagateChannelException() {
        wireSenders(inboxSender, emailSender);
        stubTemplate("AUDIT_PASS", "t", "c", List.of(1, 2));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        when(emailSender.send(any())).thenThrow(new IllegalStateException("smtp down"));

        assertThatCode(() -> dispatcher.dispatch(dto(List.of(1, 2)))).doesNotThrowAnyException();
    }

    // ------------------------------ 渠道路由正确性 ------------------------------

    @Test
    @DisplayName("渠道路由正确性：按 channel() 命中对应实现且不串道，EMAIL/SMS 目标分别取 targetEmail/targetPhone")
    void dispatchShouldRouteToCorrectSenderWithoutCrossTalk() {
        wireSenders(inboxSender, emailSender, smsSender, pushSender);
        stubTemplate("AUDIT_PASS", "t", "c", List.of(1, 2, 3, 4));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        when(emailSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        when(smsSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        when(pushSender.send(any())).thenReturn(ChannelSendResult.success("ok"));

        MessageDispatchDTO dto = dto(List.of(1, 2, 3, 4));
        dto.setTargetEmail("reader@moyue.com");
        dto.setTargetPhone("13800000000");
        dispatcher.dispatch(dto);

        ArgumentCaptor<ChannelMessage> inboxMsg = ArgumentCaptor.forClass(ChannelMessage.class);
        ArgumentCaptor<ChannelMessage> emailMsg = ArgumentCaptor.forClass(ChannelMessage.class);
        ArgumentCaptor<ChannelMessage> smsMsg = ArgumentCaptor.forClass(ChannelMessage.class);
        ArgumentCaptor<ChannelMessage> pushMsg = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(inboxSender).send(inboxMsg.capture());
        verify(emailSender).send(emailMsg.capture());
        verify(smsSender).send(smsMsg.capture());
        verify(pushSender).send(pushMsg.capture());

        assertThat(inboxMsg.getValue().getChannel()).isEqualTo(MessageChannel.INBOX);
        assertThat(emailMsg.getValue().getChannel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(smsMsg.getValue().getChannel()).isEqualTo(MessageChannel.SMS);
        assertThat(pushMsg.getValue().getChannel()).isEqualTo(MessageChannel.PUSH);

        // 目标映射：邮件取 email、短信取 phone、站内信/推送为空
        assertThat(emailMsg.getValue().getTarget()).isEqualTo("reader@moyue.com");
        assertThat(smsMsg.getValue().getTarget()).isEqualTo("13800000000");
        assertThat(inboxMsg.getValue().getTarget()).isNull();
        assertThat(pushMsg.getValue().getTarget()).isNull();
        // 不串道：每个实现只被调用一次
        verify(emailSender, times(1)).send(any());
        verify(smsSender, times(1)).send(any());
    }

    // ------------------------------ 未注册 / 未知渠道降级 ------------------------------

    @Test
    @DisplayName("渠道未注册：报 FAILED 计入 failed，且不抛异常")
    void dispatchShouldFailWhenSenderNotRegistered() {
        wireSenders(inboxSender); // 未提供 EMAIL 实现
        stubTemplate("AUDIT_PASS", "t", "c", List.of(2));

        MessageDispatchResultDTO result = dispatcher.dispatch(dto(List.of(2)));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getChannelDetails()).containsExactly("EMAIL=FAILED 渠道未注册：EMAIL");
    }

    @Test
    @DisplayName("未知渠道码：SKIP 跳过且不计入 total")
    void dispatchShouldSkipUnknownChannel() {
        wireSenders(inboxSender);
        stubTemplate("AUDIT_PASS", "t", "c", List.of(1));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));

        MessageDispatchResultDTO result = dispatcher.dispatch(dto(List.of(1, 99)));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getChannelDetails()).containsExactly("INBOX=OK", "UNKNOWN(99)=SKIP");
    }

    @Test
    @DisplayName("dto.channels 为空：回退取模板默认渠道")
    void dispatchShouldFallBackToTemplateChannelsWhenDtoChannelsEmpty() {
        wireSenders(inboxSender);
        stubTemplate("AUDIT_PASS", "t", "c", List.of(1));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));

        MessageDispatchResultDTO result = dispatcher.dispatch(dto(null));

        assertThat(result.getTotal()).isEqualTo(1);
        verify(inboxSender).send(any());
    }

    // ------------------------------ 结果状态流转 / 落记录 ------------------------------

    @Test
    @DisplayName("投递结果状态流转：success→1 且写 sendTime、pending→0、failure→2，三者均带 errorMsg 约定")
    void dispatchShouldPersistPerChannelStatus() {
        wireSenders(inboxSender, emailSender, smsSender);
        stubTemplate("AUDIT_PASS", "t", "c", List.of(1, 2, 3));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        when(emailSender.send(any())).thenReturn(ChannelSendResult.pending("no-email"));
        when(smsSender.send(any())).thenReturn(ChannelSendResult.failure("gateway-error"));

        MessageDispatchResultDTO result = dispatcher.dispatch(dto(List.of(1, 2, 3)));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);

        ArgumentCaptor<MessageChannelRecordEntity> captor =
                ArgumentCaptor.forClass(MessageChannelRecordEntity.class);
        verify(recordMapper, times(3)).insert(captor.capture());
        Map<Integer, MessageChannelRecordEntity> byChannel = captor.getAllValues().stream()
                .collect(Collectors.toMap(MessageChannelRecordEntity::getChannel, r -> r));

        MessageChannelRecordEntity ok = byChannel.get(MessageChannel.INBOX.getCode());
        assertThat(ok.getStatus()).isEqualTo(1);
        assertThat(ok.getSendTime()).isNotNull();
        assertThat(ok.getErrorMsg()).isNull();
        assertThat(ok.getRetryCount()).isZero();
        assertThat(ok.getIsDeleted()).isZero();

        MessageChannelRecordEntity pending = byChannel.get(MessageChannel.EMAIL.getCode());
        assertThat(pending.getStatus()).isZero();
        assertThat(pending.getErrorMsg()).isEqualTo("no-email");

        MessageChannelRecordEntity failed = byChannel.get(MessageChannel.SMS.getCode());
        assertThat(failed.getStatus()).isEqualTo(2);
        assertThat(failed.getErrorMsg()).isEqualTo("gateway-error");

        verify(messageTemplateService).render(anyString(), any());
    }

    @Test
    @DisplayName("写触达记录异常：仅 warn，不阻断分发主流程（结果仍正确汇总）")
    void dispatchShouldNotBlockWhenRecordInsertFails() {
        wireSenders(inboxSender);
        stubTemplate("AUDIT_PASS", "t", "c", List.of(1));
        when(inboxSender.send(any())).thenReturn(ChannelSendResult.success("ok"));
        doThrow(new RuntimeException("db down")).when(recordMapper).insert(any(MessageChannelRecordEntity.class));

        assertThatCode(() -> {
            MessageDispatchResultDTO r = dispatcher.dispatch(dto(List.of(1)));
            assertThat(r.getSuccess()).isEqualTo(1);
        }).doesNotThrowAnyException();
    }
}

package com.moyue.im;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.api.social.dto.MessageDTO;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.im.dto.SendMessageRequest;
import com.moyue.im.entity.ConversationEntity;
import com.moyue.im.entity.ConversationMemberEntity;
import com.moyue.im.entity.MessageEntity;
import com.moyue.im.mapper.ConversationMapper;
import com.moyue.im.mapper.ConversationMemberMapper;
import com.moyue.im.mapper.MessageMapper;
import com.moyue.im.service.ImService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ImService 纯 Mockito 单测（不启动 Spring 容器 / 不依赖 Docker）。
 * 覆盖 P1-11 关键路径：消息撤回 2 分钟时间窗与预览同步、已读回执推进 last_read、
 * 未读数据口径、会话成员越权校验，以及 P0-2 孤儿消息回归（会话不存在必须拒绝）。
 */
class ImServiceTest {

    /**
     * 预热 MyBatis-Plus 的 lambda 列名缓存。
     * 本测试为纯 Mockito 单测、不启动 Spring 容器，TableInfo 从未被 MyBatis 初始化，
     * 因此 markConversationRead 内部的 LambdaUpdateWrapper（MessageEntity::getStatus 等）
     * 会抛 "MybatisPlus can not find lambda cache for this entity"。手动注册实体即可。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MessageEntity.class);
        TableInfoHelper.initTableInfo(assistant, ConversationEntity.class);
        TableInfoHelper.initTableInfo(assistant, ConversationMemberEntity.class);
    }

    private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
    private final ConversationMemberMapper conversationMemberMapper = mock(ConversationMemberMapper.class);
    private final MessageMapper messageMapper = mock(MessageMapper.class);

    private final ImService service = createService();

    private ImService createService() {
        ImService s = new ImService();
        ReflectionTestUtils.setField(s, "conversationMapper", conversationMapper);
        ReflectionTestUtils.setField(s, "conversationMemberMapper", conversationMemberMapper);
        ReflectionTestUtils.setField(s, "messageMapper", messageMapper);
        // userClient / webSocketHandler 保持 null：验证安全降级路径
        return s;
    }

    private static final long CONV_ID = 500L;
    private static final long USER_ID = 1L;

    private MessageEntity message(long id, long senderId, String content, LocalDateTime createTime) {
        MessageEntity m = new MessageEntity();
        m.setId(id);
        m.setConversationId(CONV_ID);
        m.setSenderId(senderId);
        m.setContent(content);
        m.setStatus(0);
        m.setCreateTime(createTime);
        return m;
    }

    private ConversationEntity conversation(String lastMessage) {
        ConversationEntity c = new ConversationEntity();
        c.setId(CONV_ID);
        c.setType(1);
        c.setLastMessage(lastMessage);
        return c;
    }

    private ConversationMemberEntity member(long userId, Long lastReadMessageId) {
        ConversationMemberEntity m = new ConversationMemberEntity();
        m.setId(10L);
        m.setConversationId(CONV_ID);
        m.setUserId(userId);
        m.setLastReadMessageId(lastReadMessageId);
        return m;
    }

    private SendMessageRequest sendReq(Long senderId, String content) {
        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(senderId);
        req.setContent(content);
        return req;
    }

    // ------------------------------ 消息撤回（2 分钟时间窗） ------------------------------

    @Test
    @DisplayName("撤回：发送后 119 秒（2 分钟窗内）可撤回——消息逻辑删除且会话预览置为 [消息已撤回]")
    void recall_withinWindow_deletesMessageAndUpdatesPreview() {
        MessageEntity msg = message(100L, USER_ID, "你好", LocalDateTime.now().minusSeconds(119));
        ConversationEntity conv = conversation("你好");
        when(messageMapper.selectById(100L)).thenReturn(msg);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conv);

        service.recallMessage(100L, USER_ID);

        verify(messageMapper).deleteById(100L);
        assertThat(conv.getLastMessage()).isEqualTo("[消息已撤回]");
        verify(conversationMapper).updateById(conv);
    }

    @Test
    @DisplayName("撤回：发送后 121 秒（超 2 分钟窗）被拒 10001，消息不删除")
    void recall_afterWindow_rejected() {
        MessageEntity msg = message(100L, USER_ID, "你好", LocalDateTime.now().minusSeconds(121));
        when(messageMapper.selectById(100L)).thenReturn(msg);

        assertThatThrownBy(() -> service.recallMessage(100L, USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过可撤回时间")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(messageMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("撤回：非发送者本人撤回被拒 10003，消息不删除")
    void recall_byNonSender_rejected() {
        MessageEntity msg = message(100L, USER_ID, "你好", LocalDateTime.now().minusSeconds(10));
        when(messageMapper.selectById(100L)).thenReturn(msg);

        assertThatThrownBy(() -> service.recallMessage(100L, 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(messageMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("撤回：消息不存在 → 20001")
    void recall_messageMissing_notFound() {
        when(messageMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.recallMessage(999L, USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("撤回：会话最近消息并非该消息时，不改写会话预览")
    void recall_previewNotMatching_notUpdated() {
        MessageEntity msg = message(100L, USER_ID, "你好", LocalDateTime.now().minusSeconds(10));
        ConversationEntity conv = conversation("另一条消息");
        when(messageMapper.selectById(100L)).thenReturn(msg);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conv);

        service.recallMessage(100L, USER_ID);

        assertThat(conv.getLastMessage()).isEqualTo("另一条消息");
        verify(conversationMapper, never()).updateById(any(ConversationEntity.class));
    }

    // ------------------------------ 已读回执 ------------------------------

    @Test
    @DisplayName("已读回执：推进 last_read_message_id 至最新消息，并返回置为已读的他人消息条数")
    void markRead_advancesLastReadAndReturnsReadCount() {
        MessageEntity latest = message(200L, 2L, "hi", LocalDateTime.now());
        ConversationMemberEntity membership = member(USER_ID, null);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation("hi"));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(messageMapper.selectOne(any())).thenReturn(latest);
        when(messageMapper.update(any(), any())).thenReturn(3);

        int read = service.markConversationRead(CONV_ID, USER_ID);

        assertThat(read).isEqualTo(3);
        assertThat(membership.getLastReadMessageId()).isEqualTo(200L);
        verify(conversationMemberMapper).updateById(membership);
    }

    @Test
    @DisplayName("已读回执：重复标记已读幂等——第二次无未读可置，返回 0")
    void markRead_repeated_isIdempotent() {
        MessageEntity latest = message(200L, 2L, "hi", LocalDateTime.now());
        ConversationMemberEntity membership = member(USER_ID, 200L);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation("hi"));
        when(conversationMemberMapper.selectOne(any())).thenReturn(membership);
        when(messageMapper.selectOne(any())).thenReturn(latest);
        when(messageMapper.update(any(), any())).thenReturn(2).thenReturn(0);

        assertThat(service.markConversationRead(CONV_ID, USER_ID)).isEqualTo(2);
        assertThat(service.markConversationRead(CONV_ID, USER_ID)).isZero();
    }

    @Test
    @DisplayName("已读回执：非会话成员上报已读被拒 10003，不触发批量已读")
    void markRead_nonMember_forbidden() {
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation("hi"));
        when(conversationMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.markConversationRead(CONV_ID, USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(messageMapper, never()).update(any(), any());
    }

    // ------------------------------ 未读数 ------------------------------

    @Test
    @DisplayName("未读数：各会话按 conversation_id 分别统计（500→5，600→3）")
    void unreadCount_perConversation() {
        ConversationMemberEntity m500 = member(USER_ID, null);
        ConversationMemberEntity m600 = member(USER_ID, null);
        m600.setConversationId(600L);
        when(conversationMemberMapper.selectOne(any())).thenReturn(m500, m600);
        when(messageMapper.selectCount(any())).thenReturn(5L, 3L);

        assertThat(service.unreadCount(CONV_ID, USER_ID)).isEqualTo(5);
        assertThat(service.unreadCount(600L, USER_ID)).isEqualTo(3);
    }

    @Test
    @DisplayName("未读数：查询条件排除自己发送的消息（sender_id 过滤 + conversation_id 限定）")
    void unreadCount_excludesOwnMessages() {
        when(conversationMemberMapper.selectOne(any())).thenReturn(member(USER_ID, null));
        when(messageMapper.selectCount(any())).thenReturn(0L);

        service.unreadCount(CONV_ID, USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<MessageEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(messageMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("conversation_id")
                .contains("sender_id");
    }

    @Test
    @DisplayName("未读数：非会话成员查询被拒 10003")
    void unreadCount_nonMember_forbidden() {
        when(conversationMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.unreadCount(CONV_ID, USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    // ------------------------------ 发送消息（成员校验 + 孤儿消息回归） ------------------------------

    @Test
    @DisplayName("发消息：会话不存在 → 20001，且不落库（不产生孤儿消息）")
    void sendMessage_conversationMissing_notFound() {
        when(conversationMapper.selectById(CONV_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.sendMessage(CONV_ID, sendReq(USER_ID, "hello")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    @Test
    @DisplayName("发消息：非会话成员 → 10003，且不落库")
    void sendMessage_nonMember_forbidden() {
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation(null));
        when(conversationMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.sendMessage(CONV_ID, sendReq(USER_ID, "hello")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    @Test
    @DisplayName("发消息：发送人为空 → 10001")
    void sendMessage_senderNull_paramError() {
        assertThatThrownBy(() -> service.sendMessage(CONV_ID, sendReq(null, "hello")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    @Test
    @DisplayName("发消息：内容为空 → 10001")
    void sendMessage_blankContent_paramError() {
        assertThatThrownBy(() -> service.sendMessage(CONV_ID, sendReq(USER_ID, "   ")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }

    @Test
    @DisplayName("发消息：成功后落库并更新会话最近消息预览，昵称安全降级为「用户1」")
    void sendMessage_success_persistsAndUpdatesPreview() {
        ConversationEntity conv = conversation(null);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conv);
        when(conversationMemberMapper.selectOne(any())).thenReturn(member(USER_ID, null));

        MessageDTO dto = service.sendMessage(CONV_ID, sendReq(USER_ID, "hello moyue"));

        assertThat(dto.getSenderId()).isEqualTo(USER_ID);
        assertThat(dto.getContent()).isEqualTo("hello moyue");
        assertThat(dto.getType()).isEqualTo(1);
        assertThat(dto.getStatus()).isZero();
        assertThat(dto.getSenderName()).isEqualTo("用户1");
        assertThat(conv.getLastMessage()).isEqualTo("hello moyue");
        verify(messageMapper).insert(any(MessageEntity.class));
        verify(conversationMapper).updateById(conv);
    }

    @Test
    @DisplayName("发消息：超长内容（>200 字符）会话预览截断至 200 字符")
    void sendMessage_longContent_truncatesPreview() {
        ConversationEntity conv = conversation(null);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conv);
        when(conversationMemberMapper.selectOne(any())).thenReturn(member(USER_ID, null));

        service.sendMessage(CONV_ID, sendReq(USER_ID, "a".repeat(250)));

        assertThat(conv.getLastMessage()).hasSize(200);
    }
}

package com.moyue.im.service;

import com.moyue.api.account.client.UserClient;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.api.social.dto.ConversationDTO;
import com.moyue.api.social.dto.MessageDTO;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.im.dto.CreateConversationRequest;
import com.moyue.im.dto.SendMessageRequest;
import com.moyue.im.entity.ConversationEntity;
import com.moyue.im.entity.ConversationMemberEntity;
import com.moyue.im.entity.MessageEntity;
import com.moyue.im.mapper.ConversationMapper;
import com.moyue.im.mapper.ConversationMemberMapper;
import com.moyue.im.mapper.MessageMapper;
import com.moyue.im.websocket.ImWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ImService 单测（Mockito 隔离 Mapper / UserClient / WebSocket，闭环 P2-K 测试补齐）：
 * 覆盖建会话（单聊去重 / 群聊角色）、消息收发校验链（空发送人 / 空内容 / 会话不存在 / 非成员 / 成功+广播）、
 * 撤回（非本人 / 超时 / 成功+预览占位）、已读推进、未读数。
 * 雪花 ID 经 {@code doAnswer} 在 insert 时回填，对齐 MyBatis-Plus 行为。
 */
class ImServiceTest {

    private final ConversationMapper conversationMapper = mock(ConversationMapper.class);
    private final ConversationMemberMapper memberMapper = mock(ConversationMemberMapper.class);
    private final MessageMapper messageMapper = mock(MessageMapper.class);
    private final UserClient userClient = mock(UserClient.class);
    private final ImWebSocketHandler webSocketHandler = mock(ImWebSocketHandler.class);

    private ImService service;

    @BeforeEach
    void setUp() {
        service = new ImService();
        ReflectionTestUtils.setField(service, "conversationMapper", conversationMapper);
        ReflectionTestUtils.setField(service, "conversationMemberMapper", memberMapper);
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "userClient", userClient);
        ReflectionTestUtils.setField(service, "webSocketHandler", webSocketHandler);
        // 雪花 ID 回填
        doAnswer(inv -> { ((ConversationEntity) inv.getArgument(0)).setId(10L); return 1; })
                .when(conversationMapper).insert(any(ConversationEntity.class));
        doAnswer(inv -> { ((ConversationMemberEntity) inv.getArgument(0)).setId(99L); return 1; })
                .when(memberMapper).insert(any(ConversationMemberEntity.class));
        doAnswer(inv -> { ((MessageEntity) inv.getArgument(0)).setId(500L); return 1; })
                .when(messageMapper).insert(any(MessageEntity.class));
    }

    private CreateConversationRequest singleReq(Long owner, Long peer) {
        CreateConversationRequest r = new CreateConversationRequest();
        r.setType(1);
        r.setOwnerId(owner);
        r.setMemberIds(List.of(owner, peer));
        return r;
    }

    @Test
    @DisplayName("createConversation 单聊：去重成员 + 角色默认 2，返回含 memberIds 的 DTO")
    void createConversation_single_dedupMembers() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(7001L), member(7002L)));
        ConversationDTO dto = service.createConversation(singleReq(7001L, 7002L));

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getType()).isEqualTo(1);
        assertThat(dto.getMemberIds()).containsExactlyInAnyOrder(7001L, 7002L);

        ArgumentCaptor<ConversationMemberEntity> cap = ArgumentCaptor.forClass(ConversationMemberEntity.class);
        verify(memberMapper, times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(ConversationMemberEntity::getUserId)
                .containsExactlyInAnyOrder(7001L, 7002L);
        assertThat(cap.getAllValues()).allMatch(m -> m.getRole() == 2);
    }

    @Test
    @DisplayName("createConversation 群聊：设标题/群主，群主 role=1 成员 role=2")
    void createConversation_group_ownerAndMembers() {
        CreateConversationRequest r = new CreateConversationRequest();
        r.setType(2);
        r.setTitle("书友群");
        r.setOwnerId(7001L);
        r.setMemberIds(List.of(7002L, 7001L, 7003L));
        when(memberMapper.selectList(any())).thenReturn(List.of(member(7001L), member(7002L), member(7003L)));

        ConversationDTO dto = service.createConversation(r);

        assertThat(dto.getTitle()).isEqualTo("书友群");
        assertThat(dto.getOwnerId()).isEqualTo(7001L);
        // owner 自身去重，最终 3 名成员
        assertThat(dto.getMemberIds()).containsExactlyInAnyOrder(7001L, 7002L, 7003L);

        // owner(role=1) + 两名普通成员(role=2) = 3 条成员记录
        ArgumentCaptor<ConversationMemberEntity> cap = ArgumentCaptor.forClass(ConversationMemberEntity.class);
        verify(memberMapper, times(3)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(ConversationMemberEntity::getUserId)
                .containsExactlyInAnyOrder(7001L, 7002L, 7003L);
        assertThat(cap.getAllValues().stream().filter(m -> m.getUserId() == 7001L).findFirst().orElseThrow().getRole())
                .isEqualTo(1);
        assertThat(cap.getAllValues().stream().filter(m -> m.getUserId() != 7001L).map(ConversationMemberEntity::getRole)
                .toList()).containsExactly(2, 2);
    }

    @Test
    @DisplayName("sendMessage 成功：入库 + 更新会话预览 + 向成员广播")
    void sendMessage_success_broadcasts() {
        ConversationEntity conv = new ConversationEntity();
        conv.setId(10L);
        when(conversationMapper.selectById(10L)).thenReturn(conv);
        when(memberMapper.selectOne(any())).thenReturn(member(7001L));
        when(memberMapper.selectList(any())).thenReturn(List.of(member(7001L), member(7002L)));

        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(7001L);
        req.setContent("在吗");

        MessageDTO dto = service.sendMessage(10L, req);

        assertThat(dto.getId()).isEqualTo(500L);
        assertThat(dto.getContent()).isEqualTo("在吗");
        verify(conversationMapper).updateById(conv);
        assertThat(conv.getLastMessage()).isEqualTo("在吗");
        verify(webSocketHandler).broadcast(eq(List.of(7001L, 7002L)), any(MessageDTO.class));
    }

    @Test
    @DisplayName("sendMessage 会话不存在：抛 RESOURCE_NOT_FOUND，不产生孤儿消息")
    void sendMessage_conversationNotFound() {
        when(conversationMapper.selectById(10L)).thenReturn(null);

        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(7001L);
        req.setContent("hi");
        assertThatThrownBy(() -> service.sendMessage(10L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.RESOURCE_NOT_FOUND.getCode());
        verify(messageMapper, org.mockito.Mockito.never()).insert(any(MessageEntity.class));
    }

    @Test
    @DisplayName("sendMessage 非成员：抛 FORBIDDEN，不入库")
    void sendMessage_nonMember_forbidden() {
        when(conversationMapper.selectById(10L)).thenReturn(new ConversationEntity());
        when(memberMapper.selectOne(any())).thenReturn(null);

        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(7001L);
        req.setContent("hi");
        assertThatThrownBy(() -> service.sendMessage(10L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("sendMessage 空内容：抛 PARAM_ERROR")
    void sendMessage_blankContent_rejected() {
        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(7001L);
        req.setContent("   ");
        assertThatThrownBy(() -> service.sendMessage(10L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("recallMessage 非本人：抛 FORBIDDEN")
    void recallMessage_notSender_forbidden() {
        MessageEntity msg = new MessageEntity();
        msg.setId(500L);
        msg.setSenderId(7002L);
        msg.setCreateTime(LocalDateTime.now());
        when(messageMapper.selectById(500L)).thenReturn(msg);

        assertThatThrownBy(() -> service.recallMessage(500L, 7001L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("recallMessage 超过 2 分钟窗：抛 PARAM_ERROR")
    void recallMessage_expired_window() {
        MessageEntity msg = new MessageEntity();
        msg.setId(500L);
        msg.setSenderId(7001L);
        msg.setCreateTime(LocalDateTime.now().minusMinutes(5));
        when(messageMapper.selectById(500L)).thenReturn(msg);

        assertThatThrownBy(() -> service.recallMessage(500L, 7001L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("recallMessage 成功：逻辑删除消息 + 会话预览置占位文案")
    void recallMessage_success_updatesPreview() {
        MessageEntity msg = new MessageEntity();
        msg.setId(500L);
        msg.setSenderId(7001L);
        msg.setContent("secret");
        msg.setCreateTime(LocalDateTime.now());
        msg.setConversationId(10L);
        when(messageMapper.selectById(500L)).thenReturn(msg);

        ConversationEntity conv = new ConversationEntity();
        conv.setId(10L);
        conv.setLastMessage("secret");
        when(conversationMapper.selectById(10L)).thenReturn(conv);

        service.recallMessage(500L, 7001L);

        verify(messageMapper).deleteById(500L);
        verify(conversationMapper).updateById(conv);
        assertThat(conv.getLastMessage()).isEqualTo("[消息已撤回]");
    }

    @Test
    @DisplayName("markConversationRead：推进 lastReadMessageId 并返回置已读数")
    void markConversationRead_advancesCursor() {
        ConversationEntity conv = new ConversationEntity();
        conv.setId(10L);
        when(conversationMapper.selectById(10L)).thenReturn(conv);
        when(memberMapper.selectOne(any())).thenReturn(member(7001L));
        MessageEntity latest = new MessageEntity();
        latest.setId(550L);
        when(messageMapper.selectOne(any())).thenReturn(latest);
        when(messageMapper.update(any(), any())).thenReturn(3);

        int n = service.markConversationRead(10L, 7001L);

        assertThat(n).isEqualTo(3);
        ArgumentCaptor<ConversationMemberEntity> cap = ArgumentCaptor.forClass(ConversationMemberEntity.class);
        verify(memberMapper).updateById(cap.capture());
        assertThat(cap.getValue().getLastReadMessageId()).isEqualTo(550L);
    }

    @Test
    @DisplayName("unreadCount：统计 lastReadMessageId 之后的他人消息数")
    void unreadCount_countsOthersAfterRead() {
        ConversationMemberEntity m = member(7001L);
        m.setLastReadMessageId(500L);
        when(memberMapper.selectOne(any())).thenReturn(m);
        when(messageMapper.selectCount(any())).thenReturn(2L);

        assertThat(service.unreadCount(10L, 7001L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("resolveSenderName 经 UserClient 解析昵称，降级为 用户{id}")
    void senderName_resolvesOrDegrades() {
        ConversationEntity conv = new ConversationEntity();
        conv.setId(10L);
        when(conversationMapper.selectById(10L)).thenReturn(conv);
        when(memberMapper.selectOne(any())).thenReturn(member(7001L));
        when(memberMapper.selectList(any())).thenReturn(List.of(member(7001L)));

        UserDTO u = new UserDTO();
        u.setNickname("墨阅君");
        when(userClient.getUser(7001L)).thenReturn(R.ok(u));

        SendMessageRequest req = new SendMessageRequest();
        req.setSenderId(7001L);
        req.setContent("hi");
        MessageDTO dto = service.sendMessage(10L, req);
        assertThat(dto.getSenderName()).isEqualTo("墨阅君");

        // userClient 不可用：降级
        ReflectionTestUtils.setField(service, "userClient", null);
        SendMessageRequest req2 = new SendMessageRequest();
        req2.setSenderId(7002L);
        req2.setContent("yo");
        MessageDTO dto2 = service.sendMessage(10L, req2);
        assertThat(dto2.getSenderName()).isEqualTo("用户7002");
    }

    private ConversationMemberEntity member(Long userId) {
        ConversationMemberEntity m = new ConversationMemberEntity();
        m.setId(99L);
        m.setConversationId(10L);
        m.setUserId(userId);
        m.setRole(2);
        return m;
    }
}

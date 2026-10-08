package com.moyue.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageQuery;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.social.domain.entity.ImConversation;
import com.moyue.social.domain.entity.ImMember;
import com.moyue.social.domain.entity.ImMessage;
import com.moyue.social.domain.vo.ImConversationVO;
import com.moyue.social.domain.vo.ImMessageVO;
import com.moyue.social.mapper.ImConversationMapper;
import com.moyue.social.mapper.ImMemberMapper;
import com.moyue.social.mapper.ImMessageMapper;
import com.moyue.social.service.ImService;
import com.moyue.social.ws.ImSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 实现（落库 + WS 定向广播）。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImConversationMapper conversationMapper;
    private final ImMemberMapper memberMapper;
    private final ImMessageMapper messageMapper;
    private final ImSessionRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(Integer type, Long ownerId, List<Long> memberIds, String title) {
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ImConversation conv = new ImConversation();
        conv.setType(type == null ? 1 : type);
        conv.setTitle(title);
        conv.setOwnerId(ownerId);
        conversationMapper.insert(conv);

        List<Long> members = new java.util.ArrayList<>(memberIds == null ? List.of() : memberIds);
        if (!members.contains(ownerId)) {
            members.add(ownerId);
        }
        for (Long uid : members) {
            ImMember m = new ImMember();
            m.setConversationId(conv.getId());
            m.setUserId(uid);
            m.setRole(uid.equals(ownerId) ? 2 : 1);
            m.setMuted(0);
            memberMapper.insert(m);
        }
        return conv.getId();
    }

    @Override
    public List<ImConversationVO> listConversations(Long userId) {
        List<ImMember> my = memberMapper.selectList(new LambdaQueryWrapper<ImMember>()
                .eq(ImMember::getUserId, userId).eq(ImMember::getIsDeleted, 0));
        return my.stream().map(m -> toVO(m.getConversationId(), userId)).toList();
    }

    @Override
    public ImConversationVO getConversation(Long conversationId) {
        return toVO(conversationId, null);
    }

    @Override
    public List<ImMessageVO> listMessages(Long conversationId, Long userId, Long cursor, int size) {
        assertMember(conversationId, userId);
        int n = Math.max(1, Math.min(size, 100));
        var list = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getConversationId, conversationId)
                .eq(ImMessage::getIsDeleted, 0)
                .lt(cursor != null && cursor > 0, ImMessage::getId, cursor)
                .orderByDesc(ImMessage::getId)
                .last("LIMIT " + n));
        list.sort(java.util.Comparator.comparing(ImMessage::getId));
        return list.stream().map(this::toMsgVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImMessageVO send(Long conversationId, Long senderId, String content, Integer type) {
        assertMember(conversationId, senderId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }
        ImMessage msg = new ImMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setType(type == null ? 1 : type);
        msg.setStatus(1);
        messageMapper.insert(msg);

        ImConversation conv = conversationMapper.selectById(conversationId);
        conv.setLastMessage(content.length() > 80 ? content.substring(0, 80) : content);
        conv.setLastMessageTime(LocalDateTime.now());
        conversationMapper.updateById(conv);

        // 定向广播给会话成员（在线会话实时收到）
        List<Long> members = memberMapper.selectMemberIds(conversationId);
        broadcast(conversationId, senderId, msg.getId(), content, members);

        return toMsgVO(msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recall(Long userId, Long messageId) {
        ImMessage msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw BusinessException.notFound("消息");
        }
        if (!userId.equals(msg.getSenderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅发送者可撤回");
        }
        msg.setStatus(2);
        messageMapper.updateById(msg);
        List<Long> members = memberMapper.selectMemberIds(msg.getConversationId());
        broadcast(msg.getConversationId(), userId, messageId, "[撤回了一条消息]", members);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long conversationId, Long userId) {
        assertMember(conversationId, userId);
        ImMessage last = messageMapper.selectOne(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getConversationId, conversationId)
                .eq(ImMessage::getIsDeleted, 0)
                .orderByDesc(ImMessage::getId)
                .last("LIMIT 1"));
        if (last != null) {
            memberMapper.markRead(conversationId, userId, last.getId());
        }
    }

    private void assertMember(Long conversationId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long cnt = memberMapper.selectCount(new LambdaQueryWrapper<ImMember>()
                .eq(ImMember::getConversationId, conversationId)
                .eq(ImMember::getUserId, userId)
                .eq(ImMember::getIsDeleted, 0));
        if (cnt == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非会话成员");
        }
    }

    private void broadcast(Long conversationId, Long senderId, Long messageId,
                           String content, List<Long> members) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "im.message");
            payload.put("conversationId", conversationId);
            payload.put("messageId", messageId);
            payload.put("senderId", senderId);
            payload.put("content", content);
            payload.put("ts", System.currentTimeMillis());
            registry.broadcast(members, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("IM 广播失败 conversationId={} {}", conversationId, e.getMessage());
        }
    }

    private ImConversationVO toVO(Long conversationId, Long viewer) {
        ImConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            return null;
        }
        List<Long> members = memberMapper.selectMemberIds(conversationId);
        long unread = 0;
        if (viewer != null) {
            unread = memberMapper.countUnread(conversationId, viewer);
        }
        return ImConversationVO.builder()
                .id(conv.getId()).type(conv.getType()).title(conv.getTitle())
                .ownerId(conv.getOwnerId()).lastMessage(conv.getLastMessage())
                .lastMessageTime(conv.getLastMessageTime())
                .memberIds(members).unreadCount(unread).disabled(conv.getDisabled())
                .build();
    }

    private ImConversationVO toAdminVO(ImConversation conv) {
        List<Long> members = memberMapper.selectMemberIds(conv.getId());
        return ImConversationVO.builder()
                .id(conv.getId()).type(conv.getType()).title(conv.getTitle())
                .ownerId(conv.getOwnerId()).lastMessage(conv.getLastMessage())
                .lastMessageTime(conv.getLastMessageTime())
                .memberIds(members).unreadCount(0L).disabled(conv.getDisabled())
                .build();
    }

    private ImMessageVO toMsgVO(ImMessage m) {
        return ImMessageVO.builder()
                .id(m.getId()).conversationId(m.getConversationId()).senderId(m.getSenderId())
                .content(m.getContent()).type(m.getType()).status(m.getStatus())
                .createTime(m.getCreateTime())
                .build();
    }

    // ============ 运营端（管理后台） ============

    @Override
    public PageResult<ImConversationVO> adminPageConversations(PageQuery query) {
        var page = PageUtils.<ImConversation>page(query);
        var result = conversationMapper.selectPage(page, new LambdaQueryWrapper<ImConversation>()
                .orderByDesc(ImConversation::getCreateTime));
        return PageUtils.toResult(result, this::toAdminVO);
    }

    @Override
    public List<ImMessageVO> adminListMessages(Long conversationId, int size) {
        if (conversationMapper.selectById(conversationId) == null) {
            throw BusinessException.notFound("会话");
        }
        int n = Math.max(1, Math.min(size, 100));
        var list = messageMapper.selectList(new LambdaQueryWrapper<ImMessage>()
                .eq(ImMessage::getConversationId, conversationId)
                .eq(ImMessage::getIsDeleted, 0)
                .orderByDesc(ImMessage::getId)
                .last("LIMIT " + n));
        list.sort(java.util.Comparator.comparing(ImMessage::getId));
        return list.stream().map(this::toMsgVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableConversation(Long conversationId, int disabled) {
        ImConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw BusinessException.notFound("会话");
        }
        if (disabled != 0 && disabled != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "禁用值不合法（0 或 1）");
        }
        ImConversation upd = new ImConversation();
        upd.setId(conversationId);
        upd.setDisabled(disabled);
        return conversationMapper.updateById(upd) > 0;
    }
}

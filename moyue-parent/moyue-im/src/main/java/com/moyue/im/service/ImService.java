package com.moyue.im.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.client.UserClient;
import com.moyue.api.dto.ConversationDTO;
import com.moyue.api.dto.MessageDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.api.dto.UserDTO;
import com.moyue.common.BizException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 即时通讯业务：单聊 / 群聊会话、成员关系、消息收发。
 * 入库使用 Entity，出参组装 DTO；跨服务昵称解析失败安全降级。
 */
@Service
public class ImService {

    /** 单聊类型 */
    private static final int TYPE_SINGLE = 1;
    /** 群聊类型 */
    private static final int TYPE_GROUP = 2;
    /** 群主角色 */
    private static final int ROLE_OWNER = 1;
    /** 普通成员角色 */
    private static final int ROLE_MEMBER = 2;
    /** 最近消息预览截断长度 */
    private static final int PREVIEW_MAX = 200;

    /** 消息撤回时间窗（分钟，P1-11） */
    private static final int RECALL_WINDOW_MINUTES = 2;

    /** 撤回后的会话预览占位文案（P1-11） */
    private static final String RECALLED_PREVIEW = "[消息已撤回]";

    private static final Logger log = LoggerFactory.getLogger(ImService.class);

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMemberMapper conversationMemberMapper;

    @Autowired
    private MessageMapper messageMapper;

    /** 跨服务解析昵称；用户服务不可用时为 null，解析时安全降级 */
    @Autowired(required = false)
    private UserClient userClient;

    /** WebSocket 推送器；Bean 缺失时为 null，推送失败安全降级，不影响 HTTP 发送结果 */
    @Autowired(required = false)
    private ImWebSocketHandler webSocketHandler;

    /** 创建会话（单聊 / 群聊） */
    public ConversationDTO createConversation(CreateConversationRequest req) {
        ConversationEntity conv = new ConversationEntity();
        conv.setType(req.getType());
        if (TYPE_GROUP == (req.getType() == null ? 0 : req.getType())) {
            conv.setTitle(req.getTitle());
            conv.setOwnerId(req.getOwnerId());
        } else {
            conv.setTitle(null);
            conv.setOwnerId(null);
        }
        conversationMapper.insert(conv);

        if (TYPE_GROUP == (req.getType() == null ? 0 : req.getType())) {
            // 群聊：群主（ownerId）角色 1，其余 memberIds 角色 2
            ConversationMemberEntity owner = new ConversationMemberEntity();
            owner.setConversationId(conv.getId());
            owner.setUserId(req.getOwnerId());
            owner.setRole(ROLE_OWNER);
            conversationMemberMapper.insert(owner);

            if (req.getMemberIds() != null) {
                for (Long uid : req.getMemberIds()) {
                    if (uid == null || uid.equals(req.getOwnerId())) {
                        continue;
                    }
                    ConversationMemberEntity m = new ConversationMemberEntity();
                    m.setConversationId(conv.getId());
                    m.setUserId(uid);
                    m.setRole(ROLE_MEMBER);
                    conversationMemberMapper.insert(m);
                }
            }
        } else {
            // 单聊：成员 = 去重后的 [ownerId, 对方]，角色都默认 2
            Set<Long> ids = new LinkedHashSet<>();
            if (req.getOwnerId() != null) {
                ids.add(req.getOwnerId());
            }
            if (req.getMemberIds() != null) {
                ids.addAll(req.getMemberIds());
            }
            for (Long uid : ids) {
                if (uid == null) {
                    continue;
                }
                ConversationMemberEntity m = new ConversationMemberEntity();
                m.setConversationId(conv.getId());
                m.setUserId(uid);
                m.setRole(ROLE_MEMBER);
                conversationMemberMapper.insert(m);
            }
        }
        return toConversationDto(conv);
    }

    /** 列出某用户参与的会话（按最近消息时间倒序，分页） */
    public PageResult<ConversationDTO> listConversations(Long userId, int page, int size) {
        Page<ConversationEntity> p = new Page<>(page, size);
        IPage<ConversationEntity> iPage = conversationMapper.selectByUser(p, userId);

        PageResult<ConversationDTO> result = new PageResult<>();
        result.setTotal(iPage.getTotal());
        result.setPage((int) iPage.getCurrent());
        result.setSize((int) iPage.getSize());
        List<ConversationDTO> records = new ArrayList<>();
        for (ConversationEntity e : iPage.getRecords()) {
            records.add(toConversationDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /** 会话详情，聚合 memberIds；资源缺失抛异常 */
    public ConversationDTO getConversation(Long conversationId) {
        ConversationEntity e = conversationMapper.selectById(conversationId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return toConversationDto(e);
    }

    /** 会话消息分页（按发送时间升序） */
    public PageResult<MessageDTO> listMessages(Long conversationId, int page, int size) {
        Page<MessageEntity> p = new Page<>(page, size);
        messageMapper.selectPage(p, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MessageEntity>()
                .eq("conversation_id", conversationId)
                .orderByAsc("create_time"));

        PageResult<MessageDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<MessageDTO> records = new ArrayList<>();
        for (MessageEntity e : p.getRecords()) {
            records.add(toMessageDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /**
     * 发送消息：先校验后入库，再同步更新会话最近消息预览与时间。
     * 校验链：senderId 非空 → 会话存在（否则 20001）→ 发送人是会话成员（否则 10003）。
     * 修复记录：此前为「先插消息、后查会话」，对不存在会话发消息会产生孤儿消息且仍返回 200。
     */
    public MessageDTO sendMessage(Long conversationId, SendMessageRequest req) {
        if (req.getSenderId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "发送人不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "消息内容不能为空");
        }
        // 会话必须存在：不存在直接拒绝，避免产生孤儿消息
        ConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在或已删除");
        }
        // 发送人必须是会话成员：非成员拒绝，防止越权写入他人会话
        ConversationMemberEntity membership = conversationMemberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConversationMemberEntity>()
                        .eq("conversation_id", conversationId)
                        .eq("user_id", req.getSenderId()));
        if (membership == null) {
            throw new BizException(ResultCode.FORBIDDEN, "非会话成员，无权发送消息");
        }

        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setSenderId(req.getSenderId());
        msg.setContent(req.getContent());
        msg.setType(req.getType() != null ? req.getType() : 1);
        msg.setStatus(0);
        messageMapper.insert(msg);

        String preview = truncate(req.getContent(), PREVIEW_MAX);
        conv.setLastMessage(preview);
        conv.setLastMessageTime(LocalDateTime.now());
        conversationMapper.updateById(conv);

        MessageDTO dto = toMessageDto(msg);
        // 消息已落库，向会话成员实时广播；WebSocket 不可用时安全降级，不影响本次发送结果
        broadcastToMembers(conversationId, dto);
        return dto;
    }

    /**
     * 撤回消息（P1-11）：仅发送者本人可撤回，且在 2 分钟时间窗内（全局逻辑删除）。
     * 会话最近消息预览若为该消息内容，同步置为「[消息已撤回]」。
     * 重复撤回幂等（已删行视为成功）。撤回后成员拉取历史消息时全局逻辑删除自动过滤。
     */
    public void recallMessage(Long messageId, Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "操作人不能为空");
        }
        MessageEntity msg = messageMapper.selectById(messageId);
        if (msg == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "消息不存在");
        }
        if (!Objects.equals(msg.getSenderId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "仅发送者本人可撤回消息");
        }
        if (msg.getCreateTime() != null
                && msg.getCreateTime().plusMinutes(RECALL_WINDOW_MINUTES).isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.PARAM_ERROR, "超过可撤回时间（" + RECALL_WINDOW_MINUTES + " 分钟）");
        }
        messageMapper.deleteById(messageId);

        // 会话预览同步：最近消息正是被撤回的消息时更新占位文案
        ConversationEntity conv = conversationMapper.selectById(msg.getConversationId());
        if (conv != null && RECALLED_PREVIEW.equals(conv.getLastMessage()) == false
                && conv.getLastMessage() != null && conv.getLastMessage().startsWith(msg.getContent())) {
            conv.setLastMessage(RECALLED_PREVIEW);
            conversationMapper.updateById(conv);
        }
    }

    /**
     * 已读回执（P1-11）：把成员 last_read_message_id 推进到会话内最新消息，
     * 并把会话内他人发送的消息批量置为已读（status 0→1）。
     *
     * @return 本次置为已读的消息条数
     */
    public int markConversationRead(Long conversationId, Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "操作人不能为空");
        }
        ConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在或已删除");
        }
        ConversationMemberEntity membership = conversationMemberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConversationMemberEntity>()
                        .eq("conversation_id", conversationId)
                        .eq("user_id", userId));
        if (membership == null) {
            throw new BizException(ResultCode.FORBIDDEN, "非会话成员，无权上报已读");
        }

        // 会话内最新一条消息（全局逻辑删除自动过滤已撤回）
        MessageEntity latest = messageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MessageEntity>()
                        .eq("conversation_id", conversationId)
                        .orderByDesc("create_time")
                        .last("LIMIT 1"));
        if (latest != null) {
            membership.setLastReadMessageId(latest.getId());
            conversationMemberMapper.updateById(membership);
        }

        // 他人发送的未读消息批量置已读（状态原子 0→1）
        return messageMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MessageEntity>()
                        .eq(MessageEntity::getConversationId, conversationId)
                        .ne(MessageEntity::getSenderId, userId)
                        .eq(MessageEntity::getStatus, 0)
                        .set(MessageEntity::getStatus, 1));
    }

    /**
     * 会话未读数（P1-11）：last_read_message_id 之后、非本人发送的消息条数。
     * 从未上报过已读时统计全部他人消息。
     */
    public long unreadCount(Long conversationId, Long userId) {
        ConversationMemberEntity membership = conversationMemberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConversationMemberEntity>()
                        .eq("conversation_id", conversationId)
                        .eq("user_id", userId));
        if (membership == null) {
            throw new BizException(ResultCode.FORBIDDEN, "非会话成员");
        }
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MessageEntity> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MessageEntity>()
                        .eq("conversation_id", conversationId)
                        .ne("sender_id", userId);
        if (membership.getLastReadMessageId() != null) {
            // 雪花 ID 单调递增：以 ID 界定「已读之后」
            qw.gt("id", membership.getLastReadMessageId());
        }
        return messageMapper.selectCount(qw);
    }

    /**
     * 向会话成员实时广播消息。
     * 推送失败仅记录日志：HTTP 发送结果不受影响，客户端仍可通过轮询拉取历史消息。
     */
    private void broadcastToMembers(Long conversationId, MessageDTO dto) {
        if (webSocketHandler == null) {
            return;
        }
        try {
            List<Long> memberIds = aggregateMemberIds(conversationId);
            webSocketHandler.broadcast(memberIds, dto);
        } catch (Exception ex) {
            log.warn("[im] WebSocket 广播失败（已忽略），conversationId={}, messageId={}",
                    conversationId, dto.getId(), ex);
        }
    }

    /** 实体 → 会话 DTO，并聚合成员 ID 列表 */
    private ConversationDTO toConversationDto(ConversationEntity e) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(e.getId());
        dto.setType(e.getType());
        dto.setTitle(e.getTitle());
        dto.setOwnerId(e.getOwnerId());
        dto.setMemberIds(aggregateMemberIds(e.getId()));
        dto.setLastMessagePreview(e.getLastMessage());
        dto.setLastMessageTime(e.getLastMessageTime());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    /** 聚合会话成员 ID（逻辑删除由全局配置过滤） */
    private List<Long> aggregateMemberIds(Long conversationId) {
        List<ConversationMemberEntity> members = conversationMemberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConversationMemberEntity>()
                        .eq("conversation_id", conversationId));
        return members.stream().map(ConversationMemberEntity::getUserId).collect(Collectors.toList());
    }

    /** 实体 → 消息 DTO，并跨服务解析发送人昵称 */
    private MessageDTO toMessageDto(MessageEntity e) {
        MessageDTO dto = new MessageDTO();
        dto.setId(e.getId());
        dto.setConversationId(e.getConversationId());
        dto.setSenderId(e.getSenderId());
        dto.setSenderName(resolveSenderName(e.getSenderId()));
        dto.setContent(e.getContent());
        dto.setType(e.getType());
        dto.setStatus(e.getStatus());
        dto.setCreateTime(e.getCreateTime());
        return dto;
    }

    /** 解析发送人昵称：userClient 不可用 / 调用失败 / 字段为空时回退为「用户{senderId}」 */
    private String resolveSenderName(Long senderId) {
        if (senderId == null) {
            return "匿名用户";
        }
        if (userClient == null) {
            return "用户" + senderId;
        }
        try {
            UserDTO u = userClient.getUser(senderId).getData();
            if (u != null && u.getNickname() != null && !u.getNickname().isEmpty()) {
                return u.getNickname();
            }
            return "用户" + senderId;
        } catch (Exception ex) {
            // 用户服务未注册 / 不可用：安全降级，不阻断消息收发
            return "用户" + senderId;
        }
    }

    /** 截断字符串，避免预览过长 */
    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}

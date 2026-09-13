package com.moyue.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.ai.engine.AiReplyEngine;
import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.entity.AiSessionEntity;
import com.moyue.ai.mapper.AiMessageMapper;
import com.moyue.ai.mapper.AiSessionMapper;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 客服业务：会话管理 + 消息收发。
 * 每轮对话先存用户消息、再存助手回复；标题默认取首条提问前 20 字。
 * 会话归属校验：非本人会话返回 10003，防越权读取他人对话。
 */
@Service
public class AiService {

    /** 单聊标题截断长度 */
    private static final int TITLE_MAX = 20;

    /** 角色：1 用户 / 2 助手 */
    private static final int ROLE_USER = 1;
    private static final int ROLE_ASSISTANT = 2;

    @Autowired
    private AiSessionMapper sessionMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private AiReplyEngine replyEngine;

    /**
     * 一轮对话：sessionId 为空则新建会话；校验归属后落用户消息，
     * 经回复引擎生成回复并落库，返回助手消息。内容为空抛 PARAM_ERROR。
     */
    @Transactional
    public AiMessageEntity chat(Long userId, Long sessionId, String content) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "消息内容不能为空");
        }

        AiSessionEntity session;
        if (sessionId == null) {
            session = new AiSessionEntity();
            session.setUserId(userId);
            session.setTitle(truncate(content, TITLE_MAX));
            session.setIsDeleted(0);
            LocalDateTime now = LocalDateTime.now();
            session.setCreateTime(now);
            session.setUpdateTime(now);
            sessionMapper.insert(session);
        } else {
            session = sessionMapper.selectById(sessionId);
            if (session == null) {
                throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在或已删除");
            }
            if (!session.getUserId().equals(userId)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权访问他人会话");
            }
        }

        saveMessage(session.getId(), ROLE_USER, content);
        String reply = replyEngine.reply(content);
        return saveMessage(session.getId(), ROLE_ASSISTANT, reply);
    }

    /** 某用户会话分页（按最近更新倒序） */
    public PageResult<AiSessionEntity> pageSessions(Long userId, int page, int size) {
        Page<AiSessionEntity> p = new Page<>(page, size);
        QueryWrapper<AiSessionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("update_time");
        sessionMapper.selectPage(p, wrapper);

        PageResult<AiSessionEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return result;
    }

    /** 会话消息列表（按发送时间升序）；非本人会话抛 FORBIDDEN */
    public List<AiMessageEntity> listMessages(Long userId, Long sessionId) {
        AiSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在或已删除");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问他人会话");
        }
        QueryWrapper<AiMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId).orderByAsc("create_time");
        return messageMapper.selectList(wrapper);
    }

    /** 落一条消息 */
    private AiMessageEntity saveMessage(Long sessionId, int role, String content) {
        AiMessageEntity msg = new AiMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setIsDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        msg.setCreateTime(now);
        msg.setUpdateTime(now);
        messageMapper.insert(msg);
        return msg;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}

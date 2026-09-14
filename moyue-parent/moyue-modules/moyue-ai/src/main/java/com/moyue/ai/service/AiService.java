package com.moyue.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.ai.engine.AiReplyEngine;
import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.entity.AiSessionEntity;
import com.moyue.ai.mapper.AiMessageMapper;
import com.moyue.ai.mapper.AiSessionMapper;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 客服业务：会话管理 + 消息收发。
 * 每轮对话先存用户消息、再存助手回复；标题默认取首条提问前 20 字。
 * 会话归属校验：非本人会话返回 10003，防越权读取他人对话。
 *
 * <p>问答索引同步：一轮对话（提问 + 回复）落库成功后，异步经 {@link SearchIndexClient}
 * 推送 moyue-search 建 ES 索引（moyue-qa）；推送失败仅记 warn、不影响对话主流程。</p>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

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

    /** 检索服务客户端（问答索引同步 hook）；search 未注册时安全降级 */
    @Autowired(required = false)
    private SearchIndexClient searchIndexClient;

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
        AiMessageEntity answer = saveMessage(session.getId(), ROLE_ASSISTANT, reply);
        // 索引同步 hook：一轮对话（提问 + 回复）落库成功后异步推送 ES 索引
        indexQaAsync(session.getId(), content, answer);
        return answer;
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

    // ------------------------------ 问答索引同步 hook ------------------------------

    /**
     * 异步推送一轮问答（提问 + 回复）到 moyue-search 建 ES 索引。
     * <p>ai 模块未启用 @EnableAsync，此处用 CompletableFuture 公共池异步执行；
     * 推送失败（含客户端降级 40002 / 未注册 / 网络异常）仅记 warn，不影响对话主流程，
     * 可经管理端全量重建补偿。</p>
     */
    private void indexQaAsync(Long sessionId, String question, AiMessageEntity answer) {
        if (searchIndexClient == null || answer == null || answer.getId() == null) {
            return;
        }
        QaIndexDTO dto = new QaIndexDTO();
        // 一轮对话一条文档：messageId 取助手回复消息 ID 作为 _id（幂等覆盖）
        dto.setMessageId(answer.getId());
        dto.setSessionId(sessionId);
        dto.setQuestion(question);
        dto.setAnswer(answer.getContent());
        dto.setCreateTime(answer.getCreateTime());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                searchIndexClient.indexQa(dto);
            } catch (Exception ex) {
                log.warn("同步问答索引失败 messageId={}, err={}", answer.getId(), ex.getMessage());
            }
        });
    }

    /**
     * 分页拉取全量问答对（内部端点专用，不经网关）：一轮对话（提问 + 回复）合并为一条
     * {@link QaIndexDTO}，按 ai_message.id 升序；供 moyue-search 管理端全量重建 moyue-qa 索引。
     *
     * <p>配对口径：role=1（用户提问）与其后紧跟的 role=2（助手回复）配对；页边界处
     * 悬空消息（页首回复 / 页尾提问）跨页配对，由下一页补齐，total 为消息数非文档数（近似值）。</p>
     */
    public PageResult<QaIndexDTO> pageQaForIndex(int page, int size) {
        Page<AiMessageEntity> p = new Page<>(page, size);
        QueryWrapper<AiMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("id");
        messageMapper.selectPage(p, wrapper);

        List<QaIndexDTO> records = new ArrayList<>();
        AiMessageEntity pendingQuestion = null;
        for (AiMessageEntity msg : p.getRecords()) {
            if (msg.getRole() != null && msg.getRole() == ROLE_USER) {
                pendingQuestion = msg;
            } else if (msg.getRole() != null && msg.getRole() == ROLE_ASSISTANT) {
                QaIndexDTO dto = new QaIndexDTO();
                dto.setMessageId(msg.getId());
                dto.setSessionId(msg.getSessionId());
                dto.setQuestion(pendingQuestion == null ? "" : pendingQuestion.getContent());
                dto.setAnswer(msg.getContent());
                dto.setCreateTime(msg.getCreateTime());
                records.add(dto);
                pendingQuestion = null;
            }
        }

        PageResult<QaIndexDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(records);
        return result;
    }

    // TODO: 会话删除（逻辑删除 ai_session）目前无业务入口；若后续新增删除功能，
    // 须同步调用 searchIndexClient.removeQaBySession(sessionId) 清理 ES 索引文档。

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}

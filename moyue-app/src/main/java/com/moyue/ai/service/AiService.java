package com.moyue.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.ai.engine.AiReplyEngine;
import com.moyue.ai.engine.ChatTurn;
import com.moyue.ai.engine.KeywordRuleReplyEngine;
import com.moyue.ai.engine.ReplyContext;
import com.moyue.ai.engine.ReplyResult;
import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.entity.AiSessionEntity;
import com.moyue.ai.mapper.AiMessageMapper;
import com.moyue.ai.mapper.AiSessionMapper;
import com.moyue.api.search.client.QaSearchClient;
import com.moyue.api.search.client.SearchIndexClient;
import com.moyue.api.search.dto.QaContextDTO;
import com.moyue.api.search.dto.QaIndexDTO;
import com.moyue.common.R;
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
import java.util.Collections;
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

    /** 历史多轮注入上限（仅 LLM 引擎消费） */
    private static final int HISTORY_LIMIT = 10;

    /** 低置信时追加的转人工提示 */
    private static final String HUMAN_HANDOFF =
            "\n（如需人工协助，请回复「人工」，工作日 10:00-18:00 在线）";

    /** 角色：1 用户 / 2 助手 */
    private static final int ROLE_USER = 1;
    private static final int ROLE_ASSISTANT = 2;

    @Autowired
    private AiSessionMapper sessionMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    /** 主回复引擎：注入 @Primary 的 LlmReplyEngine（未启用时返回 null，由 AiService 级联兜底） */
    @Autowired
    private AiReplyEngine replyEngine;

    /** 兜底引擎：关键词规则，永远可用 */
    @Autowired
    private KeywordRuleReplyEngine keywordReplyEngine;

    /** 检索服务客户端（问答索引同步 hook）；search 未注册时安全降级 */
    @Autowired(required = false)
    private SearchIndexClient searchIndexClient;

    /** RAG 召回客户端（仅 LLM 引擎用，注入参考知识库）；search 未注册时安全降级 */
    @Autowired(required = false)
    private QaSearchClient qaSearchClient;

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
            if (session.getIsDeleted() != null && session.getIsDeleted() == 1) {
                throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在或已删除");
            }
            if (!session.getUserId().equals(userId)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权访问他人会话");
            }
        }

        saveMessage(session.getId(), ROLE_USER, content);

        // 组装回复上下文：历史多轮（RAG 仅 LLM 引擎消费，缺失安全降级）+ 当前提问
        ReplyContext ctx = new ReplyContext();
        ctx.setUserId(userId);
        ctx.setSessionId(session.getId());
        ctx.setContent(content);
        ctx.setHistory(loadHistory(session.getId()));
        ctx.setRagContext(retrieveRagContext(content));

        // 级联：主引擎（LLM）无法回答（content 为 null）→ 兜底关键字引擎
        ReplyResult result = replyEngine.reply(ctx);
        if (result == null || result.getContent() == null) {
            result = keywordReplyEngine.reply(ctx);
        }

        String reply = (result != null && result.getContent() != null)
                ? result.getContent() : keywordReplyEngine.reply(ctx).getContent();
        // 低置信：追加转人工提示（兜底引擎未命中规则时 confident=false）
        if (result != null && !result.isConfident()) {
            reply = reply + HUMAN_HANDOFF;
        }

        AiMessageEntity answer = saveMessage(session.getId(), ROLE_ASSISTANT, reply);
        // 索引同步 hook：一轮对话（提问 + 回复）落库成功后异步推送 ES 索引
        indexQaAsync(session.getId(), content, answer);
        return answer;
    }

    /** 加载会话最近 HISTORY_LIMIT 条历史轮次（按时间升序），供 LLM 多轮上下文 */
    private List<ChatTurn> loadHistory(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<AiMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId)
                .eq("is_deleted", 0)
                .orderByDesc("create_time")
                .last("LIMIT " + HISTORY_LIMIT);
        List<AiMessageEntity> msgs = messageMapper.selectList(wrapper);
        Collections.reverse(msgs);
        List<ChatTurn> turns = new ArrayList<>(msgs.size());
        for (AiMessageEntity m : msgs) {
            turns.add(new ChatTurn(m.getRole() == null ? 0 : m.getRole(), m.getContent()));
        }
        return turns;
    }

    /** RAG 召回：经检索服务取 topK 问答片段拼接（search 未注册 / 异常时返回 null，安全降级） */
    private String retrieveRagContext(String question) {
        if (qaSearchClient == null || question == null || question.isBlank()) {
            return null;
        }
        try {
            R<QaContextDTO> resp = qaSearchClient.retrieveContext(question);
            if (resp == null || resp.getData() == null
                    || resp.getData().getPassages() == null
                    || resp.getData().getPassages().isEmpty()) {
                return null;
            }
            return String.join("\n\n", resp.getData().getPassages());
        } catch (Exception ex) {
            log.warn("RAG 召回失败，跳过知识库注入 userId 忽略：{}", ex.getMessage());
            return null;
        }
    }

    /** 某用户会话分页（按最近更新倒序） */
    public PageResult<AiSessionEntity> pageSessions(Long userId, int page, int size) {
        Page<AiSessionEntity> p = new Page<>(page, size);
        QueryWrapper<AiSessionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("is_deleted", 0).orderByDesc("update_time");
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
        wrapper.eq("session_id", sessionId).eq("is_deleted", 0).orderByAsc("create_time");
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

    /**
     * 删除单个会话（逻辑删除，闭环 AiService:291 TODO）：级联逻辑删除其全部消息，
     * 并异步清理 ES 问答索引（非阻断）。会话不存在 / 已删除 → RESOURCE_NOT_FOUND；
     * 非本人会话 → FORBIDDEN（防越权删他人对话）。
     */
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID 与会话 ID 不能为空");
        }
        AiSessionEntity session = sessionMapper.selectOne(
                new QueryWrapper<AiSessionEntity>().eq("id", sessionId).eq("is_deleted", 0));
        if (session == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在或已删除");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权删除他人会话");
        }
        session.setIsDeleted(1);
        sessionMapper.updateById(session);
        // 级联逻辑删除消息（is_deleted: 0 → 1）
        UpdateWrapper<AiMessageEntity> uw = new UpdateWrapper<>();
        uw.eq("session_id", sessionId).eq("is_deleted", 0).set("is_deleted", 1);
        messageMapper.update(null, uw);
        removeQaIndexAsync(sessionId);
    }

    /**
     * 清空用户全部会话（逻辑删除，闭环 AiService:291 TODO）：逐会话级联删除消息并清理 ES 索引；
     * 返回删除的会话数（仅统计未删除会话）。
     */
    @Transactional
    public long clearSessions(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        List<AiSessionEntity> sessions = sessionMapper.selectList(
                new QueryWrapper<AiSessionEntity>().eq("user_id", userId).eq("is_deleted", 0));
        for (AiSessionEntity s : sessions) {
            s.setIsDeleted(1);
            sessionMapper.updateById(s);
            UpdateWrapper<AiMessageEntity> uw = new UpdateWrapper<>();
            uw.eq("session_id", s.getId()).eq("is_deleted", 0).set("is_deleted", 1);
            messageMapper.update(null, uw);
            removeQaIndexAsync(s.getId());
        }
        return sessions.size();
    }

    /** 异步清理某会话的 ES 问答索引文档（非阻断：search 未注册 / 异常仅记 warn，不影响删除主流程） */
    private void removeQaIndexAsync(Long sessionId) {
        if (searchIndexClient == null) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                searchIndexClient.removeQaBySession(sessionId);
            } catch (Exception ex) {
                log.warn("清理会话 ES 问答索引失败 sessionId={}, err={}", sessionId, ex.getMessage());
            }
        });
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}

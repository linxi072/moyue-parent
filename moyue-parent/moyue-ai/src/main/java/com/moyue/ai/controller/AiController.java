package com.moyue.ai.controller;

import com.moyue.ai.entity.AiMessageEntity;
import com.moyue.ai.entity.AiSessionEntity;
import com.moyue.ai.service.AiService;
import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 智能客服接口：对话 / 会话列表 / 会话消息。
 * 路径前缀 /api/v1 与网关路由保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class AiController {

    @Autowired
    private AiService aiService;

    /** 一轮对话：sessionId 为空自动新建会话；返回助手回复消息 */
    @PostMapping("/ai/chat")
    public R<AiMessageEntity> chat(@RequestBody ChatRequest req) {
        return R.ok(aiService.chat(req.getUserId(), req.getSessionId(), req.getContent()));
    }

    /** 某用户会话分页（按最近更新倒序） */
    @GetMapping("/ai/sessions")
    public R<PageResult<AiSessionEntity>> listSessions(@RequestParam Long userId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(aiService.pageSessions(userId, page, size));
    }

    /** 会话消息列表（按发送时间升序）；非本人会话返回 10003 */
    @GetMapping("/ai/sessions/{sessionId}/messages")
    public R<List<AiMessageEntity>> listMessages(@PathVariable Long sessionId,
                                                 @RequestParam Long userId) {
        return R.ok(aiService.listMessages(userId, sessionId));
    }

    /** 对话请求体 */
    public static class ChatRequest {

        private Long userId;

        private Long sessionId;

        private String content;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}

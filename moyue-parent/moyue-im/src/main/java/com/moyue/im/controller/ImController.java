package com.moyue.im.controller;

import com.moyue.api.dto.ConversationDTO;
import com.moyue.api.dto.MessageDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import com.moyue.im.dto.CreateConversationRequest;
import com.moyue.im.dto.SendMessageRequest;
import com.moyue.im.service.ImService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 即时通讯接口：会话（单聊 / 群聊）、成员、消息收发。
 * 路径前缀 /api/v1 与网关路由、Feign ImClient 保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class ImController {

    @Autowired
    private ImService imService;

    /** 创建会话（单聊 / 群聊） */
    @PostMapping("/im/conversations")
    public R<ConversationDTO> createConversation(@RequestBody CreateConversationRequest req) {
        return R.ok(imService.createConversation(req));
    }

    /** 列出某用户参与的会话（分页，按最近消息时间倒序） */
    @GetMapping("/im/conversations")
    public R<PageResult<ConversationDTO>> listConversations(@RequestParam Long userId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return R.ok(imService.listConversations(userId, page, size));
    }

    /** 会话详情（聚合成员 ID） */
    @GetMapping("/im/conversations/{conversationId}")
    public R<ConversationDTO> getConversation(@PathVariable Long conversationId) {
        return R.ok(imService.getConversation(conversationId));
    }

    /** 会话消息分页（按发送时间升序） */
    @GetMapping("/im/conversations/{conversationId}/messages")
    public R<PageResult<MessageDTO>> listMessages(@PathVariable Long conversationId,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return R.ok(imService.listMessages(conversationId, page, size));
    }

    /** 发送消息 */
    @PostMapping("/im/conversations/{conversationId}/messages")
    public R<MessageDTO> sendMessage(@PathVariable Long conversationId,
                                    @RequestBody SendMessageRequest req) {
        return R.ok(imService.sendMessage(conversationId, req));
    }
}

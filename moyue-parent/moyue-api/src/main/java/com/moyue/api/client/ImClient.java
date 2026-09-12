package com.moyue.api.client;

import com.moyue.api.dto.ConversationDTO;
import com.moyue.api.dto.MessageDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 即时通讯服务 Feign 客户端（moyue-im）。
 * 返回类型包裹 R&lt;T&gt;，与控制器实际响应结构一致；DTO 为实体的字段子集，
 * Jackson 默认忽略未知字段，可安全反序列化。
 */
@FeignClient(name = "moyue-im")
public interface ImClient {

    /** 查询用户会话列表（单聊 + 群聊） */
    @GetMapping("/api/v1/im/conversations")
    R<PageResult<ConversationDTO>> listConversations(@RequestParam("userId") Long userId,
                                                     @RequestParam("page") int page,
                                                     @RequestParam("size") int size);

    /** 查询会话消息（分页） */
    @GetMapping("/api/v1/im/conversations/{conversationId}/messages")
    R<PageResult<MessageDTO>> listMessages(@PathVariable("conversationId") Long conversationId,
                                           @RequestParam("page") int page,
                                           @RequestParam("size") int size);
}

package com.moyue.api.message.client;

import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.message.dto.MessageDispatchResultDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 触达服务 Feign 客户端（moyue-message）。
 * 返回类型包裹 R&lt;T&gt;，与 MessageInternalController 内部端点结构一致。
 * 供 moyue-risk（审核 / 举报结果触达）与 moyue-platform（定时触达 Job）调用（P2-14）。
 */
@FeignClient(name = "moyue-message", fallbackFactory = MessageDispatchClientFallbackFactory.class)
public interface MessageDispatchClient {

    /** 触发一次消息分发（按模板渲染 + 多渠道投递 + 落触达记录） */
    @PostMapping("/api/v1/internal/messages/dispatch")
    R<MessageDispatchResultDTO> dispatch(@RequestBody MessageDispatchDTO dto);
}

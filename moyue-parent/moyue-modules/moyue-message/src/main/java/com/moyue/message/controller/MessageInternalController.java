package com.moyue.message.controller;

import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.message.dto.MessageDispatchResultDTO;
import com.moyue.common.core.domain.R;
import com.moyue.message.service.MessageDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 触达内部端点（P2-14 N-1/N-2）：供服务间（risk / platform）触发消息分发。
 * {@code /api/v1/internal/**} 不经网关（服务间经 Nacos {@code lb://} 直连）。
 */
@RestController
@RequestMapping("/api/v1")
public class MessageInternalController {

    @Autowired
    private MessageDispatcher messageDispatcher;

    /** 触发一次消息分发（按模板渲染 + 多渠道投递 + 落触达记录） */
    @PostMapping("/internal/messages/dispatch")
    public R<MessageDispatchResultDTO> dispatch(@RequestBody MessageDispatchDTO dto) {
        return R.ok(messageDispatcher.dispatch(dto));
    }
}

package com.moyue.api.message.client;

import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.message.dto.MessageDispatchResultDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.message.service.MessageDispatcher;
import org.springframework.stereotype.Component;

/**
 * 触达服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-message) 已移除 OpenFeign，改为直接注入 {@link MessageDispatcher} 委托调用。
 */
@Component
public class MessageDispatchClient {

    private final MessageDispatcher messageDispatcher;

    public MessageDispatchClient(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
    }

    /** 触发一次消息分发（按模板渲染 + 多渠道投递 + 落触达记录） */
    public R<MessageDispatchResultDTO> dispatch(MessageDispatchDTO dto) {
        try {
            return R.ok(messageDispatcher.dispatch(dto));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

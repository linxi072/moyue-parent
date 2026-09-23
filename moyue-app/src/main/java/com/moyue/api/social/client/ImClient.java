package com.moyue.api.social.client;

import com.moyue.api.social.dto.ConversationDTO;
import com.moyue.api.social.dto.MessageDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.im.service.ImService;
import org.springframework.stereotype.Component;

/**
 * 即时通讯服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-social) 已移除 OpenFeign，改为直接注入 {@link ImService} 委托调用。
 */
@Component
public class ImClient {

    private final ImService imService;

    public ImClient(ImService imService) {
        this.imService = imService;
    }

    /** 查询用户会话列表（单聊 + 群聊） */
    public R<PageResult<ConversationDTO>> listConversations(Long userId, int page, int size) {
        try {
            return R.ok(imService.listConversations(userId, page, size));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }

    /** 查询会话消息（分页） */
    public R<PageResult<MessageDTO>> listMessages(Long conversationId, int page, int size) {
        try {
            return R.ok(imService.listMessages(conversationId, page, size));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

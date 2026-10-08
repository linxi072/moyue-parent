package com.moyue.social.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.social.domain.vo.ImConversationVO;
import com.moyue.social.domain.vo.ImMessageVO;
import com.moyue.social.service.ImService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 即时通讯（C 端）：会话 / 消息 / 撤回 / 已读。
 *
 * <p>消息发送经 REST 持久化后由 {@code ImSessionRegistry} 做 WS 实时广播。
 *
 * @author moyue
 */
@Tag(name = "即时通讯", description = "会话创建/列表/详情、消息收发、撤回、已读")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/im/conversations")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;

    @Operation(summary = "创建会话（单聊 type=1 / 群聊 type=2）")
    @PostMapping
    public R<Long> create(@RequestBody CreateConvBody body) {
        return R.ok(imService.createConversation(body.type(), currentUser(), body.memberIds(), body.title()));
    }

    @Operation(summary = "我的会话列表")
    @GetMapping
    public R<List<ImConversationVO>> list() {
        return R.ok(imService.listConversations(currentUser()));
    }

    @Operation(summary = "会话详情")
    @GetMapping("/{conversationId}")
    public R<ImConversationVO> detail(@PathVariable Long conversationId) {
        return R.ok(imService.getConversation(conversationId));
    }

    @Operation(summary = "会话消息列表（游标分页，cursor 传上一页最小 id）")
    @GetMapping("/{conversationId}/messages")
    public R<List<ImMessageVO>> messages(@PathVariable Long conversationId,
                                        @RequestParam(required = false) Long cursor,
                                        @RequestParam(defaultValue = "30") int size) {
        return R.ok(imService.listMessages(conversationId, currentUser(), cursor, size));
    }

    @Operation(summary = "发送消息（落库 + WS 广播）")
    @PostMapping("/{conversationId}/messages")
    public R<ImMessageVO> send(@PathVariable Long conversationId, @RequestBody SendBody body) {
        return R.ok(imService.send(conversationId, currentUser(), body.content(), body.type()));
    }

    @Operation(summary = "撤回消息（仅发送者）")
    @PostMapping("/{conversationId}/messages/{messageId}/recall")
    public R<Boolean> recall(@PathVariable Long conversationId, @PathVariable Long messageId) {
        imService.recall(currentUser(), messageId);
        return R.ok(true);
    }

    @Operation(summary = "标记会话已读")
    @PostMapping("/{conversationId}/read")
    public R<Boolean> read(@PathVariable Long conversationId) {
        imService.markRead(conversationId, currentUser());
        return R.ok(true);
    }

    private Long currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    /** 创建会话入参 */
    public record CreateConvBody(Integer type, List<Long> memberIds, String title) {
    }

    /** 发送消息入参 */
    public record SendBody(String content, Integer type) {
    }
}

package com.moyue.message.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.message.domain.dto.query.MessageQuery;
import com.moyue.message.domain.vo.MessageVO;
import com.moyue.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内信收件箱（C 端用户）。
 *
 * <p>身份取自 {@link UserContext}（网关注入头派生），所有查询与读取操作归属当前登录用户，
 * 不接受前端传入 userId（与书架 / 评论等 C 端接口一致的架构约定）。
 * 网关路由 {@code /api/v1/messages/**} 已指向本服务，无需新增路由。
 *
 * @author moyue
 */
@Tag(name = "站内信收件箱", description = "我的消息列表、未读计数、标记已读 / 全部已读")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/messages")
@RequiredArgsConstructor
public class MessageConsumerController {

    private final MessageService messageService;

    @Operation(summary = "我的消息列表", description = "分页返回当前用户的站内信，支持按类型 / 已读标记过滤")
    @GetMapping
    public R<PageResult<MessageVO>> list(MessageQuery query) {
        return R.ok(messageService.pageMyMessages(currentUser(), query));
    }

    @Operation(summary = "我的未读消息数")
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount(currentUser()));
    }

    @Operation(summary = "标记已读", description = "仅作用于本人消息，越权 ID 不生效")
    @PostMapping("/read")
    public R<Boolean> read(@RequestBody List<Long> ids) {
        return R.ok(messageService.readMine(currentUser(), ids));
    }

    @Operation(summary = "全部已读", description = "标记本人全部消息为已读")
    @PostMapping("/read-all")
    public R<Boolean> readAll() {
        return R.ok(messageService.readAllMine(currentUser()));
    }

    private Long currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }
}

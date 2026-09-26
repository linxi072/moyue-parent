package com.moyue.message.center;

import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.security.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息中心接口：统一收件箱 / 未读角标 / 按类型筛选 / 已读。
 *
 * <p>身份统一取自 {@link SecurityContextHolder#currentUserId()}（与付费模块一致），
 * 不读取请求头。匿名请求统一返回 {@link ResultCode#UNAUTHORIZED}。</p>
 */
@RestController
@RequestMapping("/api/v1/message-center")
public class MessageCenterController {

    @Autowired
    private MessageCenterService messageCenterService;

    /** 统一收件箱：GET /api/v1/message-center/inbox?type=&unreadOnly=false&page=1&size=20 */
    @GetMapping("/inbox")
    public R<PageResult<InboxItemDTO>> inbox(
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = requireUserId();
        PageResult<InboxItemDTO> result = messageCenterService.listInbox(userId, type, unreadOnly, page, size);
        return R.ok(result);
    }

    /** 未读角标：GET /api/v1/message-center/unread */
    @GetMapping("/unread")
    public R<UnreadSummaryVO> unread() {
        Long userId = requireUserId();
        return R.ok(messageCenterService.unreadSummary(userId));
    }

    /** 标记单条已读：PUT /api/v1/message-center/read/{noticeId} */
    @PutMapping("/read/{noticeId}")
    public R<Void> read(@PathVariable Long noticeId) {
        Long userId = requireUserId();
        messageCenterService.markRead(userId, noticeId);
        return R.ok();
    }

    /** 全部标记已读：PUT /api/v1/message-center/read-all */
    @PutMapping("/read-all")
    public R<Void> readAll() {
        Long userId = requireUserId();
        messageCenterService.markAllRead(userId);
        return R.ok();
    }

    /** 取当前登录用户；匿名返回 null 时抛未授权异常 */
    private Long requireUserId() {
        Long userId = SecurityContextHolder.currentUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}

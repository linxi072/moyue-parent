package com.moyue.message.controller;

import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.message.entity.NoticeEntity;
import com.moyue.message.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息接口：站内通知查询 / 发送 / 标记已读（16-22）。
 */
@RestController
@RequestMapping("/api/v1")
public class MessageController {

    @Autowired
    private MessageService messageService;

    /** 查询指定用户的全部通知（unreadOnly=true 仅返回未读） */
    @GetMapping("/messages/{userId}")
    public R<List<NoticeEntity>> listByUser(@PathVariable Long userId,
                                            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return R.ok(messageService.listByUser(userId, unreadOnly));
    }

    @PostMapping("/messages")
    public R<Void> send(@RequestBody NoticeEntity notice) {
        messageService.send(notice);
        return R.ok();
    }

    /** 标记单条已读（仅通知归属人可操作）：PUT /api/v1/messages/{noticeId}/read */
    @PutMapping("/messages/{noticeId}/read")
    public R<Void> markRead(@PathVariable Long noticeId, HttpServletRequest request) {
        messageService.markRead(noticeId, requireUserId(request));
        return R.ok();
    }

    /** 全部标记已读（数据库层原子更新，返回已读条数）：PUT /api/v1/messages/users/{userId}/read-all */
    @PutMapping("/messages/users/{userId}/read-all")
    public R<Integer> markAllRead(@PathVariable Long userId, HttpServletRequest request) {
        long current = requireUserId(request);
        if (current != userId) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作他人通知");
        }
        return R.ok(messageService.markAllRead(userId));
    }

    // ------------------------------ 上下文工具 ------------------------------

    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}

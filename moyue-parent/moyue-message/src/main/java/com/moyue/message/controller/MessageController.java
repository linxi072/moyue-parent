package com.moyue.message.controller;

import com.moyue.common.R;
import com.moyue.message.entity.NoticeEntity;
import com.moyue.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息接口：站内通知查询 / 发送。
 */
@RestController
@RequestMapping("/api/v1")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/messages/{userId}")
    public R<List<NoticeEntity>> listByUser(@PathVariable Long userId) {
        return R.ok(messageService.listByUser(userId));
    }

    @PostMapping("/messages")
    public R<Void> send(@RequestBody NoticeEntity notice) {
        messageService.send(notice);
        return R.ok();
    }
}

package com.moyue.bookshelf.controller;

import com.moyue.api.social.dto.BookshelfNotifyDTO;
import com.moyue.bookshelf.websocket.BookshelfWebSocketHandler;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 书架 WebSocket 内部通知端点（P2-D，服务间调用，仅供 Feign 使用）。
 * 路径 {@code /api/v1/internal/**} 受 {@code InternalAuthInterceptor} 的 X-Service-Token 校验覆盖（P2-I）。
 * 接收 moyue-content 经 {@code WsNotifyClient} 推来的通知，转发给目标用户在线端；
 * 目标用户无在线会话时直接返回 {@code R.ok()}（no-op），不报错。
 */
@RestController
@RequestMapping("/api/v1/internal/ws/bookshelf")
public class WsNotifyController {

    @Autowired
    private BookshelfWebSocketHandler bookshelfWebSocketHandler;

    @PostMapping("/notify")
    public R<Void> notifyBookshelf(@RequestBody BookshelfNotifyDTO dto) {
        bookshelfWebSocketHandler.pushEvent(dto.getUserId(), dto);
        return R.ok();
    }
}

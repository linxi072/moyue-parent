package com.moyue.api.social.client;

import com.moyue.api.social.dto.BookshelfNotifyDTO;
import com.moyue.bookshelf.websocket.BookshelfWebSocketHandler;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.springframework.stereotype.Component;

/**
 * 书架 WebSocket 通知进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-social) 已移除 OpenFeign；monolith 内 bookshelf 的 WebSocket 推送
 * 由本进程的 {@link BookshelfWebSocketHandler} 直接完成（即原 {@code WsNotifyController} 内部端点
 * 落到的真实 Bean），故委托到它，等价于原先「经 Feign 推到 moyue-social 触发近实时推送」的语义。
 *
 * <p>注意：未委托到 {@code BookshelfWsNotifier}，因其以构造器注入本 Client 监听书架变更事件后回调，
 * 若反向注入会形成构造器循环依赖；真正的 WS 推送动作由 {@link BookshelfWebSocketHandler} 承载。</p>
 */
@Component
public class WsNotifyClient {

    private final BookshelfWebSocketHandler bookshelfWebSocketHandler;

    public WsNotifyClient(BookshelfWebSocketHandler bookshelfWebSocketHandler) {
        this.bookshelfWebSocketHandler = bookshelfWebSocketHandler;
    }

    /** 通知目标用户在线端推送书架变更（best-effort） */
    public R<Void> notifyBookshelf(BookshelfNotifyDTO dto) {
        try {
            bookshelfWebSocketHandler.pushEvent(dto.getUserId(), dto);
            return R.ok();
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

package com.moyue.api.social.client;

import com.moyue.api.social.dto.BookshelfNotifyDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 书架 WebSocket 通知 Feign 客户端（目标服务 moyue-social，P2-D）。
 * 由 moyue-content 的 {@code BookshelfWsNotifier} 在书架变更事件后调用，
 * 经内部端点 {@code /api/v1/internal/ws/bookshelf/notify} 触发近实时推送。
 *
 * <p>contextId=wsNotifyClient 与同服务的 ImClient / BlogClient / CommentClient（name 同为
 * moyue-social）区分注册，避免 FeignClientSpecification 同名 bean 冲突。</p>
 *
 * <p>该请求命中 {@code /api/v1/internal/} 前缀，由 moyue-common-security 的
 * {@code ServiceTokenRequestInterceptor} 自动注入 {@code X-Service-Token}，与下游
 * {@code InternalAuthInterceptor} 校验侧对齐（P2-I）。</p>
 */
@FeignClient(name = "moyue-social", contextId = "wsNotifyClient",
        fallbackFactory = WsNotifyClientFallbackFactory.class)
public interface WsNotifyClient {

    /** 通知 moyue-social 向目标用户在线端推送书架变更（best-effort） */
    @PostMapping("/api/v1/internal/ws/bookshelf/notify")
    R<Void> notifyBookshelf(@RequestBody BookshelfNotifyDTO dto);
}

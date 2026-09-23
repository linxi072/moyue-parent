package com.moyue.read.event;

import com.moyue.api.social.client.WsNotifyClient;
import com.moyue.api.social.dto.BookshelfNotifyDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 书架变更 → WebSocket 通知转发器（P2-D）。
 * 监听 {@link BookshelfChangedEvent}，组装 {@link BookshelfNotifyDTO} 并经 {@link WsNotifyClient}
 * 通知 moyue-social 向目标用户在线端推送。
 *
 * <p><b>节流</b>：同 (userId+bookId) 在 1s 窗口内只推送末次事件——采用「前导立即 + 尾部去抖合并」：
 * 首事件立即出（保证近实时），窗口内后续高频事件合并为一次尾部推送并携带最新（末次）事件，
 * 既避免翻章高频写把 WS 连接打爆，又保证客户端最终看到最新状态。</p>
 *
 * <p><b>可靠性</b>：任何异常仅 {@code log.warn}，绝不阻断阅读主流程；下游 moyue-social 不可用时
 * WsNotifyClient 已由 FallbackFactory 降级返回 40002，本组件吞掉即可。</p>
 */
@Component
public class BookshelfWsNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookshelfWsNotifier.class);

    /** 节流窗口（毫秒） */
    private static final long WINDOW_MS = 1000L;

    private final WsNotifyClient wsNotifyClient;

    /** 组合节流状态：上次发送时间 / 待发的尾部任务 / 最新待发事件 */
    private final Map<String, Long> lastSend = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Map<String, BookshelfChangedEvent> latest = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "bookshelf-ws-notify");
        t.setDaemon(true);
        return t;
    });

    public BookshelfWsNotifier(WsNotifyClient wsNotifyClient) {
        this.wsNotifyClient = wsNotifyClient;
    }

    @EventListener(BookshelfChangedEvent.class)
    public void onChanged(BookshelfChangedEvent event) {
        try {
            if (event == null || event.getUserId() == null || event.getBookId() == null) {
                return;
            }
            String key = event.getUserId() + "#" + event.getBookId();
            latest.put(key, event);
            long now = System.currentTimeMillis();
            Long last = lastSend.get(key);
            if (last == null || now - last >= WINDOW_MS) {
                // 前导：立即推送
                lastSend.put(key, now);
                doSend(key, event);
                return;
            }
            // 窗口内：仅在尚无待发尾部任务时安排一次尾部推送（携带最新事件）
            pending.computeIfAbsent(key, k ->
                    scheduler.schedule(() -> {
                        pending.remove(k);
                        BookshelfChangedEvent ev = latest.remove(k);
                        if (ev == null) {
                            return;
                        }
                        lastSend.put(k, System.currentTimeMillis());
                        doSend(k, ev);
                    }, WINDOW_MS - (now - last), TimeUnit.MILLISECONDS));
        } catch (Exception ex) {
            log.warn("[bookshelf-ws] 通知处理异常（已忽略，不阻断主流程）：userId={}, bookId={}",
                    event == null ? null : event.getUserId(),
                    event == null ? null : event.getBookId(), ex);
        }
    }

    /** 组装 DTO 并调用客户端（best-effort，异常仅记日志） */
    private void doSend(String key, BookshelfChangedEvent event) {
        try {
            BookshelfNotifyDTO dto = new BookshelfNotifyDTO();
            dto.setUserId(event.getUserId());
            dto.setBookId(event.getBookId());
            dto.setAction(event.getAction());
            dto.setEventId(event.getEventId());
            dto.setTimestamp(event.getOccurredAt());
            wsNotifyClient.notifyBookshelf(dto);
        } catch (Exception ex) {
            log.warn("[bookshelf-ws] 调用 WsNotifyClient 失败（已忽略，不阻断主流程）：key={}", key, ex);
        }
    }

    /** 关闭调度器，避免进程关闭/测试时守护线程泄漏 */
    @PreDestroy
    public void destroy() {
        scheduler.shutdownNow();
    }
}

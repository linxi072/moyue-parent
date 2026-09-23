package com.moyue.read.event;

import com.moyue.api.social.client.WsNotifyClient;
import com.moyue.api.social.dto.BookshelfNotifyDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

/**
 * BookshelfWsNotifier 单测（b）：验证监听书架变更事件后调用 WsNotifyClient，并验证节流与异常吞没。
 * 纯单测，直接调用 {@code onChanged(...)}（不走 Spring 事件多播器），WsNotifyClient 用 Mockito mock。
 */
class BookshelfWsNotifierTest {

    private BookshelfWsNotifier notifier;

    @AfterEach
    void tearDown() {
        if (notifier != null) {
            notifier.destroy();
        }
    }

    private BookshelfChangedEvent evt(long userId, long bookId, String action, long eventId) {
        return new BookshelfChangedEvent(this, userId, bookId, action, eventId, System.currentTimeMillis());
    }

    @Test
    @DisplayName("单次变更：立即调用 WsNotifyClient 且携带正确 DTO 字段")
    void singleEvent_callsClient() {
        WsNotifyClient client = mock(WsNotifyClient.class);
        notifier = new BookshelfWsNotifier(client);

        notifier.onChanged(evt(1L, 100L, BookshelfChangedEvent.ACTION_ADD, 1L));

        verify(client, times(1)).notifyBookshelf(org.mockito.ArgumentMatchers.argThat((BookshelfNotifyDTO dto) ->
                dto.getUserId().equals(1L)
                        && dto.getBookId().equals(100L)
                        && BookshelfChangedEvent.ACTION_ADD.equals(dto.getAction())
                        && dto.getEventId().equals(1L)));
    }

    @Test
    @DisplayName("1s 窗口内高频变更：前导立即 + 尾部去抖，末次事件被推送且仅调用两次")
    void burstWithinWindow_coalescesToFirstAndLast() throws Exception {
        WsNotifyClient client = mock(WsNotifyClient.class);
        notifier = new BookshelfWsNotifier(client);

        notifier.onChanged(evt(1L, 100L, BookshelfChangedEvent.ACTION_PROGRESS, 1L)); // 前导立即
        notifier.onChanged(evt(1L, 100L, BookshelfChangedEvent.ACTION_PROGRESS, 2L)); // 尾部排期
        notifier.onChanged(evt(1L, 100L, BookshelfChangedEvent.ACTION_PROGRESS, 3L)); // 更新末次

        // 窗口内（尾部尚未触发）应仅有前导一次调用
        verify(client, timeout(200).times(1)).notifyBookshelf(any());

        // 等待窗口结束，尾部触发携带末次事件
        Thread.sleep(1200);
        verify(client, times(2)).notifyBookshelf(any());

        BookshelfNotifyDTO last = captureLast(client);
        assertThat(last.getEventId()).isEqualTo(3L); // 末次事件
    }

    @Test
    @DisplayName("客户端抛异常：仅吞掉，不向上抛出")
    void clientThrows_exceptionSwallowed() {
        WsNotifyClient client = mock(WsNotifyClient.class);
        doThrow(new RuntimeException("moyue-social down")).when(client).notifyBookshelf(any());
        notifier = new BookshelfWsNotifier(client);

        // 不应抛异常
        notifier.onChanged(evt(1L, 100L, BookshelfChangedEvent.ACTION_ADD, 1L));
        verify(client, times(1)).notifyBookshelf(any());
    }

    private BookshelfNotifyDTO captureLast(WsNotifyClient client) {
        org.mockito.ArgumentCaptor<BookshelfNotifyDTO> captor =
                org.mockito.ArgumentCaptor.forClass(BookshelfNotifyDTO.class);
        verify(client, atLeastOnce()).notifyBookshelf(captor.capture());
        java.util.List<BookshelfNotifyDTO> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }
}

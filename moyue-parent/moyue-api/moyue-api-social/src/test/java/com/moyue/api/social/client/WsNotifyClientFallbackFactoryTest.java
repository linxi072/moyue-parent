package com.moyue.api.social.client;

import com.moyue.api.social.dto.BookshelfNotifyDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WsNotifyClientFallbackFactory 单测：目标服务不可用时返回 R.fail(40002) = SERVICE_DEGRADED，
 * 调用方可据此优雅降级（不抛异常）。
 */
class WsNotifyClientFallbackFactoryTest {

    @Test
    @DisplayName("客户端失败时 fallback 返回 R.fail(40002) = SERVICE_DEGRADED")
    void fallback_returnsServiceDegraded() {
        WsNotifyClientFallbackFactory factory = new WsNotifyClientFallbackFactory();
        WsNotifyClient fallback = factory.create(new RuntimeException("moyue-social down"));

        R<Void> resp = fallback.notifyBookshelf(new BookshelfNotifyDTO());

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(ResultCode.SERVICE_DEGRADED.getCode());
        assertThat(resp.getCode()).isEqualTo(40002);
    }
}

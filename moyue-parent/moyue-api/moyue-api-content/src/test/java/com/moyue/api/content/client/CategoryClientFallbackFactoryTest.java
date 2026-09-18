package com.moyue.api.content.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CategoryClientFallbackFactory 单测：目标服务不可用时返回 R.fail(40002) = SERVICE_DEGRADED，
 * 调用方可据此优雅降级（不抛异常）。对齐 WsNotifyClientFallbackFactoryTest。
 */
class CategoryClientFallbackFactoryTest {

    @Test
    @DisplayName("客户端失败时 fallback 返回 R.fail(40002) = SERVICE_DEGRADED")
    void fallback_returnsServiceDegraded() {
        CategoryClientFallbackFactory factory = new CategoryClientFallbackFactory();
        CategoryClient fallback = factory.create(new RuntimeException("moyue-content down"));

        R<?> resp = fallback.listCategories();

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(ResultCode.SERVICE_DEGRADED.getCode());
        assertThat(resp.getCode()).isEqualTo(40002);
    }
}

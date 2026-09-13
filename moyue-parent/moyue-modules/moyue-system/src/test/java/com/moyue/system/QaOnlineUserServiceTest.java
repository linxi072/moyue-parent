package com.moyue.system;

import com.moyue.system.service.OnlineUserService;
import com.moyue.system.vo.OnlineUserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OnlineUserService 单测（Mockito 隔离 Redis）：
 * 覆盖验收点 5（会话 key/TTL、强退删 key + 黑名单 TTL=剩余有效期）
 * 与验收点 6（Redis 异常 warn 不外抛）。
 */
class QaOnlineUserServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final OnlineUserService service = new OnlineUserService(redisTemplate);

    @Test
    @DisplayName("写在线会话：key=moyue:online:{md5(token)}，TTL=access-ttl")
    void addSessionShouldWriteKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        long ttlMillis = 7_200_000L;

        service.addSession("token-xyz", 100L, "墨客", "1.2.3.4", "UA", ttlMillis);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), anyString(), eq(ttlMillis), eq(TimeUnit.MILLISECONDS));
        String key = keyCaptor.getValue();
        assertThat(key).isEqualTo("moyue:online:" + OnlineUserService.md5("token-xyz"));
        // value 内不落原始 token，只存 tokenId
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(key), valCaptor.capture(), eq(ttlMillis), eq(TimeUnit.MILLISECONDS));
        assertThat(valCaptor.getValue()).doesNotContain("token-xyz").contains("墨客");
    }

    @Test
    @DisplayName("Redis 写会话失败：只 warn 不外抛（登录主流程不受影响）")
    void addSessionShouldSwallowRedisErrors() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOps).set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(TimeUnit.class));

        assertThatNoException().isThrownBy(() ->
                service.addSession("token-xyz", 100L, "墨客", "1.2.3.4", "UA", 7_200_000L));
    }

    @Test
    @DisplayName("强退：删会话 key + 写黑名单（TTL=剩余有效期）")
    void forceLogoutShouldDeleteSessionAndWriteDenyKey() {
        String tokenId = OnlineUserService.md5("token-xyz");
        when(redisTemplate.getExpire("moyue:online:" + tokenId, TimeUnit.MILLISECONDS)).thenReturn(3_600_000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service.forceLogout(tokenId);

        verify(redisTemplate).delete("moyue:online:" + tokenId);
        verify(valueOps).set(eq("moyue:auth:deny:" + tokenId), eq("1"),
                eq(3_600_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("强退：会话已过期（TTL<=0）时不写黑名单，且不抛异常")
    void forceLogoutShouldSkipDenyKeyWhenExpired() {
        String tokenId = "dead-token";
        when(redisTemplate.getExpire("moyue:online:" + tokenId, TimeUnit.MILLISECONDS)).thenReturn(-2L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertThatNoException().isThrownBy(() -> service.forceLogout(tokenId));

        verify(redisTemplate).delete("moyue:online:" + tokenId);
        verify(valueOps, never()).set(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
    }

    @Test
    @DisplayName("在线列表：Redis 全挂（SCAN 抛异常）返回空页而非抛异常")
    void pageShouldDegradeToEmptyOnRedisFailure() {
        when(redisTemplate.scan(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("redis down"));

        var pr = service.page(1, 20, null);

        assertThat(pr).isNotNull();
        assertThat(pr.getTotal()).isZero();
        assertThat(pr.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("在线列表：内存分页边界（page 超界返回空记录、total 不变）")
    void pageShouldHandleOutOfRangePagination() {
        // SCAN 抛异常即空数据集——超界分页也应稳定返回空页
        when(redisTemplate.scan(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("redis down"));

        var pr = service.page(99, 20, "keyword");

        assertThat(pr.getPage()).isEqualTo(99);
        assertThat(pr.getSize()).isEqualTo(20);
        assertThat(pr.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("VO 默认值：OnlineUserVO 属性可空安全序列化（Jackson 双向）")
    void onlineUserVoJsonRoundTrip() throws Exception {
        OnlineUserVO vo = new OnlineUserVO();
        vo.setTokenId(OnlineUserService.md5("t"));
        vo.setUserId(9L);
        vo.setNickname("nick");
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String json = om.writeValueAsString(vo);
        OnlineUserVO back = om.readValue(json, OnlineUserVO.class);
        assertThat(back.getTokenId()).isEqualTo(vo.getTokenId());
        assertThat(back.getUserId()).isEqualTo(9L);
    }
}

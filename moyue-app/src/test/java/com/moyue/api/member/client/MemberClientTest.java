package com.moyue.api.member.client;

import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 会员客户端跨域方法单测（P2-B 权益框架深化）。
 * 验证 getDiscountRate / getBadge 委托 MemberService，且服务异常时降级 SERVICE_DEGRADED。
 */
@ExtendWith(MockitoExtension.class)
class MemberClientTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberClient memberClient;

    @Test
    void getDiscountRate_delegatesToService() {
        when(memberService.getDiscountRate(1L)).thenReturn(new BigDecimal("0.90"));
        R<BigDecimal> r = memberClient.getDiscountRate(1L);
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualByComparingTo("0.90");
    }

    @Test
    void getBadge_delegatesToService() {
        when(memberService.getBadge(1L)).thenReturn("vip");
        R<String> r = memberClient.getBadge(1L);
        assertThat(r.getCode()).isZero();
        assertThat(r.getData()).isEqualTo("vip");
    }

    @Test
    void getDiscountRate_serviceThrows_degrades() {
        when(memberService.getDiscountRate(1L)).thenThrow(new RuntimeException("db down"));
        R<BigDecimal> r = memberClient.getDiscountRate(1L);
        assertThat(r.getCode()).isEqualTo(ResultCode.SERVICE_DEGRADED.getCode());
    }
}

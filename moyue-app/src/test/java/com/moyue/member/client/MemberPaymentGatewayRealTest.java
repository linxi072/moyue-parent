package com.moyue.member.client;

import com.moyue.member.config.MemberPayProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会员真实支付网关单测（P2-B 可插拔骨架）。
 * 直接 new 实例化（构造函数注入 MemberPayProperties）：缺密钥 / 仅设渠道类型 / 配置齐全（占位）均降级成功，
 * 即 isSuccess()=true 且 isRealSuccess()=false，绝不抛未捕获异常。
 */
class MemberPaymentGatewayRealTest {

    @Test
    void noCredentials_degraded() {
        MemberPaymentGatewayReal real = new MemberPaymentGatewayReal(new MemberPayProperties());
        MemberPaymentGateway.ChargeResult r = real.charge(1L, new BigDecimal("10.00"), "BIZ-1");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isRealSuccess()).isFalse();
    }

    @Test
    void channelTypeOnly_stillDegradedPlaceholder() {
        MemberPayProperties props = new MemberPayProperties();
        props.setChannelType("WECHAT");
        // appId/secret/mchId 仍缺失 → 同样降级
        MemberPaymentGatewayReal real = new MemberPaymentGatewayReal(props);
        MemberPaymentGateway.ChargeResult r = real.charge(1L, new BigDecimal("10.00"), "BIZ-2");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isRealSuccess()).isFalse();
    }

    @Test
    void allConfigured_stillDegradedPlaceholder() {
        MemberPayProperties props = new MemberPayProperties();
        props.setChannelType("ALIPAY");
        props.setAppId("appid");
        props.setSecret("secret");
        props.setMchId("mchid");
        // 真实渠道仅占位，仍返回降级成功（isRealSuccess=false）
        MemberPaymentGatewayReal real = new MemberPaymentGatewayReal(props);
        MemberPaymentGateway.ChargeResult r = real.charge(1L, new BigDecimal("10.00"), "BIZ-3");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isRealSuccess()).isFalse();
    }
}

package com.moyue.member.client;

import com.moyue.member.config.MemberPayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 会员订阅真实支付网关（P2-B 可插拔骨架）。
 *
 * <p>仅当 {@code moyue.member.pay.channel=real} 时经 {@link ConditionalOnProperty} 激活；
 * 与默认 {@code MemberPaymentGatewayStub}（channel=stub / 缺省）互斥，不会同时注册，无 Bean 冲突。</p>
 *
 * <p>配置驱动优雅降级：若密钥（appId/secret/mchId）或渠道类型（channelType）缺失，或真实渠道调用抛异常，
 * 一律在网关内 {@code log.warn} 并返回 {@link MemberPaymentGateway.ChargeResult#degraded(String)}
 * （success=true, realSuccess=false）——业务主流程（subscribe / 续费）据此放行但不延长有效期，
 * 绝不向上抛出未捕获异常。P2-B 真实渠道仅占位（微信 / 支付宝 / 通联），P2-1 再细化某家对接。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "moyue.member.pay.channel", havingValue = "real")
public class MemberPaymentGatewayReal implements MemberPaymentGateway {

    private final MemberPayProperties props;

    /** 构造函数注入（Spring 装配 / 单测 {@code new MemberPaymentGatewayReal(props)} 均可）。 */
    public MemberPaymentGatewayReal(MemberPayProperties props) {
        this.props = props;
    }

    @Override
    public ChargeResult charge(Long userId, BigDecimal amount, String bizNo) {
        String channelType = props.getChannelType();
        String appId = props.getAppId();
        String secret = props.getSecret();
        String mchId = props.getMchId();

        // 配置缺失 → 降级放行，不阻断业务主链路
        if (isBlank(channelType) || isBlank(appId) || isBlank(secret) || isBlank(mchId)) {
            log.warn("会员真实支付渠道配置缺失，降级放行（不真实扣款）：channelType={}, appId={}, mchId={}",
                    channelType, mask(appId), mask(mchId));
            return ChargeResult.degraded(channelType);
        }

        // 配置齐全 → 按渠道类型分支调占位 adapter；异常仍降级，绝不抛出
        try {
            ChannelType type = ChannelType.valueOf(channelType.trim().toUpperCase());
            switch (type) {
                case WECHAT:
                    return chargeWechat(userId, amount, bizNo, appId, secret, mchId);
                case ALIPAY:
                    return chargeAlipay(userId, amount, bizNo, appId, secret, mchId);
                case ALLIN:
                    return chargeAllin(userId, amount, bizNo, appId, secret, mchId);
                default:
                    return ChargeResult.degraded(channelType);
            }
        } catch (Exception e) {
            log.warn("会员真实支付渠道调用异常，降级放行：channelType={}, bizNo={}, error={}",
                    channelType, bizNo, e.getMessage());
            return ChargeResult.degraded(channelType);
        }
    }

    /** 微信支付占位（P2-B 无真实 SDK，构造请求后即降级成功） */
    private ChargeResult chargeWechat(Long userId, BigDecimal amount, String bizNo,
                                     String appId, String secret, String mchId) {
        log.info("会员微信支付占位调用 userId={} amount={} bizNo={} appId={}", userId, amount, bizNo, mask(appId));
        return ChargeResult.degraded(ChannelType.WECHAT.name());
    }

    /** 支付宝占位（P2-B 无真实 SDK，构造请求后即降级成功） */
    private ChargeResult chargeAlipay(Long userId, BigDecimal amount, String bizNo,
                                      String appId, String secret, String mchId) {
        log.info("会员支付宝支付占位调用 userId={} amount={} bizNo={} appId={}", userId, amount, bizNo, mask(appId));
        return ChargeResult.degraded(ChannelType.ALIPAY.name());
    }

    /** 通联支付占位（P2-B 无真实 SDK，构造请求后即降级成功） */
    private ChargeResult chargeAllin(Long userId, BigDecimal amount, String bizNo,
                                     String appId, String secret, String mchId) {
        log.info("会员通联支付占位调用 userId={} amount={} bizNo={} appId={}", userId, amount, bizNo, mask(appId));
        return ChargeResult.degraded(ChannelType.ALLIN.name());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 密钥脱敏，避免日志泄露 */
    private static String mask(String s) {
        if (s == null || s.length() <= 4) {
            return "***";
        }
        return s.substring(0, 2) + "***" + s.substring(s.length() - 2);
    }
}

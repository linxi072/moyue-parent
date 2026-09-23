package com.moyue.member.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

/**
 * 会员订阅扣款空跑桩（P2-B 默认生效）。
 * 不接真实支付渠道、不需要密钥；生成模拟流水号并记日志，返回成功。
 * 由 {@code moyue.member.pay.channel=stub}（缺省即生效，matchIfMissing=true）激活；
 * 接入真实渠道时改配为对应实现即可，业务侧无感知。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "moyue.member.pay.channel", havingValue = "stub", matchIfMissing = true)
public class MemberPaymentGatewayStub implements MemberPaymentGateway {

    private static final Random RANDOM = new Random();

    @Override
    public ChargeResult charge(Long userId, BigDecimal amount, String bizNo) {
        String paySerial = "DRY" + System.currentTimeMillis() + String.format("%04d", RANDOM.nextInt(10000));
        log.info("DRY-RUN member charge userId={} amount={} bizNo={} serial={}", userId, amount, bizNo, paySerial);
        return ChargeResult.success(paySerial, "stub");
    }
}

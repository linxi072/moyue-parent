package com.moyue.operation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

/**
 * 打款空跑桩（P0-1 默认生效）。
 * 不接任何真实支付渠道、不需要任何密钥；生成模拟流水号并记日志，返回成功。
 * 由 {@code moyue.pay.channel=stub}（缺省即生效，matchIfMissing=true）激活；
 * 接入真实渠道时改配为对应实现即可，业务侧无感知。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "moyue.pay.channel", havingValue = "stub", matchIfMissing = true)
public class PayChannelGatewayStub implements PayChannelGateway {

    private static final Random RANDOM = new Random();

    @Override
    public PayResult payout(Long authorId, BigDecimal amount) {
        String paySerial = "DRY" + System.currentTimeMillis() + String.format("%04d", RANDOM.nextInt(10000));
        log.info("DRY-RUN payout authorId={} amount={} serial={}", authorId, amount, paySerial);
        return PayResult.success(paySerial, "stub");
    }
}

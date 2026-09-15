package com.moyue.operation.service;

import com.moyue.operation.client.PayChannelGateway;
import com.moyue.operation.client.PayChannelGateway.PayResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 结算打款委托服务：封装可插拔支付网关调用，向 SettlementService 返回 PayResult。
 * 网关实现经 Spring 注入（默认 PayChannelGatewayStub 空跑桩，无需任何密钥）。
 */
@Service
public class SettlementPayoutService {

    @Autowired(required = false)
    private PayChannelGateway payChannelGateway;

    /**
     * 发起打款；无可用网关时返回失败结果（不抛异常，交由状态机标记 3）。
     */
    public PayResult payout(Long authorId, BigDecimal amount) {
        if (payChannelGateway == null) {
            PayResult failed = new PayResult();
            failed.setSuccess(false);
            failed.setChannel("none");
            return failed;
        }
        return payChannelGateway.payout(authorId, amount);
    }
}

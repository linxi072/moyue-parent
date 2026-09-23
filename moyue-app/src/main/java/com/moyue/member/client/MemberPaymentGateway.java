package com.moyue.member.client;

import java.math.BigDecimal;

/**
 * 会员订阅支付网关抽象（P2-B）。方向为「用户付费开通」，与结算打款的
 * {@code PayChannelGateway}（作者收款）相反；业务侧仅依赖本接口，具体渠道以独立实现注入。
 * 默认生效 {@link MemberPaymentGatewayStub}（空跑桩，不需要密钥）。
 */
public interface MemberPaymentGateway {

    /**
     * 对用户扣款开通订阅。
     *
     * @param userId 用户 ID
     * @param amount 扣款金额（元）
     * @param bizNo  业务单号（订阅订单号）
     * @return 扣款结果（含成功标志、渠道流水号、渠道标识）
     */
    ChargeResult charge(Long userId, BigDecimal amount, String bizNo);

    /** 扣款结果。 */
    class ChargeResult {
        private boolean success;
        private String paySerial;
        private String channel;

        public ChargeResult() {
        }

        /** 构造成功结果 */
        public static ChargeResult success(String paySerial, String channel) {
            ChargeResult r = new ChargeResult();
            r.success = true;
            r.paySerial = paySerial;
            r.channel = channel;
            return r;
        }

        /** 构造失败结果 */
        public static ChargeResult failed(String channel) {
            ChargeResult r = new ChargeResult();
            r.success = false;
            r.channel = channel;
            return r;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getPaySerial() {
            return paySerial;
        }

        public void setPaySerial(String paySerial) {
            this.paySerial = paySerial;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }
    }
}

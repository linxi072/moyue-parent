package com.moyue.operation.client;

import java.math.BigDecimal;

/**
 * 可插拔支付渠道网关抽象（P0-1）。
 * 业务侧仅依赖本接口，具体渠道（微信 / 支付宝 / 银行代发）以独立实现注入；
 * 默认生效 {@link PayChannelGatewayStub}（空跑桩，不需要任何密钥）。
 */
public interface PayChannelGateway {

    /**
     * 向作者打款。
     *
     * @param authorId 作者 ID
     * @param amount   打款金额（元）
     * @return 打款结果（含成功标志、渠道流水号、渠道标识）
     */
    PayResult payout(Long authorId, BigDecimal amount);

    /** 打款结果。 */
    class PayResult {
        private boolean success;
        private String paySerial;
        private String channel;

        public PayResult() {
        }

        /** 构造成功结果 */
        public static PayResult success(String paySerial, String channel) {
            PayResult r = new PayResult();
            r.success = true;
            r.paySerial = paySerial;
            r.channel = channel;
            return r;
        }

        /** 构造失败结果 */
        public static PayResult failed(String channel) {
            PayResult r = new PayResult();
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

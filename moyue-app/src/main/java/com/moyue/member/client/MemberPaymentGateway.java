package com.moyue.member.client;

import java.math.BigDecimal;

/**
 * 会员订阅支付网关抽象（P2-B）。方向为「用户付费开通」，与结算打款的
 * {@code PayChannelGateway}（作者收款）相反；业务侧仅依赖本接口，具体渠道以独立实现注入。
 * 默认生效 {@link MemberPaymentGatewayStub}（空跑桩，不需要密钥）。
 *
 * <p>续费语义：{@code success=true & realSuccess=true} 真实扣款成功；
 * {@code success=true & realSuccess=false} 网关降级（缺密钥/调用失败但业务放行）；
 * {@code success=false} 网关判定失败（Stub 不会返回）。{@code subscribe} 只看 {@link ChargeResult#isSuccess()}；
 * 续费 Job 额外看 {@link ChargeResult#isRealSuccess()} 决定是否延长有效期。</p>
 */
public interface MemberPaymentGateway {

    /**
     * 支付渠道类型（仅当 {@code moyue.member.pay.channel=real} 时按此分支真实渠道骨架；P2-B 仅占位）。
     */
    enum ChannelType {
        WECHAT,
        ALIPAY,
        ALLIN
    }

    /**
     * 对用户扣款开通订阅 / 续费。
     *
     * @param userId 用户 ID
     * @param amount 扣款金额（元）
     * @param bizNo  业务单号（订阅订单号 / 续费单号 RENEW-{id}-{epoch}）
     * @return 扣款结果（含成功标志、真实成功标志、渠道流水号、渠道标识）
     */
    ChargeResult charge(Long userId, BigDecimal amount, String bizNo);

    /** 扣款结果。 */
    class ChargeResult {
        private boolean success;
        /** 真实扣款是否成功：true=渠道真实扣款；false=网关降级（业务放行但真实未扣款） */
        private boolean realSuccess;
        private String paySerial;
        private String channel;

        public ChargeResult() {
        }

        /** 构造成功结果（真实扣款成功） */
        public static ChargeResult success(String paySerial, String channel) {
            ChargeResult r = new ChargeResult();
            r.success = true;
            r.realSuccess = true;
            r.paySerial = paySerial;
            r.channel = channel;
            return r;
        }

        /** 构造失败结果（网关判定失败，Stub 不会返回） */
        public static ChargeResult failed(String channel) {
            ChargeResult r = new ChargeResult();
            r.success = false;
            r.realSuccess = false;
            r.channel = channel;
            return r;
        }

        /**
         * 构造「降级成功」结果：业务成功放行（success=true）但真实扣款未成功（realSuccess=false）。
         * 用于真实渠道缺密钥 / 调用异常时，不阻断 subscribe 主流程、续费 Job 标记续费失败。
         *
         * @param channel 渠道标识（通常传 ChannelType 名，生成 "real-degraded-{channel}" 便于追踪）
         */
        public static ChargeResult degraded(String channel) {
            ChargeResult r = new ChargeResult();
            r.success = true;
            r.realSuccess = false;
            r.channel = "real-degraded-" + (channel == null ? "" : channel);
            return r;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public boolean isRealSuccess() {
            return realSuccess;
        }

        public void setRealSuccess(boolean realSuccess) {
            this.realSuccess = realSuccess;
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

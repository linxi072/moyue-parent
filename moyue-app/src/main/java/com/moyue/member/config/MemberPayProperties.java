package com.moyue.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会员支付网关配置（P2-B 可插拔骨架）。
 *
 * <p>绑定 {@code moyue.member.pay.*}（channel 由 application.yml 经环境变量
 * {@code MOYUE_MEMBER_PAY_CHANNEL} 注入，缺省 stub）；真实渠道密钥走环境变量
 * {@code MOYUE_MEMBER_PAY_CHANNEL_TYPE / APP_ID / SECRET / MCH_ID} 占位，密钥类不留明文。</p>
 *
 * <p>本类仅承载配置，不引入任何新 Maven 依赖，由 Spring Boot 原生 {@link ConfigurationProperties} 绑定。</p>
 */
@Component
@ConfigurationProperties(prefix = "moyue.member.pay")
public class MemberPayProperties {

    /** 支付渠道主开关：stub（缺省）/ real */
    private String channel;

    /** 真实渠道类型：WECHAT / ALIPAY / ALLIN（对应 MemberPaymentGateway.ChannelType） */
    private String channelType;

    /** 应用 ID（微信 appId / 支付宝 appId / 通联 cusid） */
    private String appId;

    /** 渠道密钥（微信 APIv3 密钥 / 支付宝私钥 / 通联 MD5 key） */
    private String secret;

    /** 商户号（微信 mchId / 支付宝 sellerId / 通联 mchId） */
    private String mchId;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getMchId() {
        return mchId;
    }

    public void setMchId(String mchId) {
        this.mchId = mchId;
    }
}

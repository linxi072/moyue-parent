package com.moyue.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 网关信任头 HMAC-SHA256 签名工具（P2-I P1）。
 * 对 {@code method + "\n" + path + "\n" + timestamp} 签名，供网关侧签名与业务侧验签共用，
 * 避免跨模块重复实现。secret 为空时返回空串（no-op）。
 */
public final class SecuritySignUtil {

    /** 计算网关信任头签名；secret 为空返回空串 */
    public static String sign(String method, String path, String timestamp, String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((method + "\n" + path + "\n" + timestamp).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "";
        }
    }

    private SecuritySignUtil() {
    }
}

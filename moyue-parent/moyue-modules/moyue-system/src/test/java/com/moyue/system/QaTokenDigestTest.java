package com.moyue.system;

import com.moyue.system.config.TokenDenyInterceptor;
import com.moyue.system.service.OnlineUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MD5 会话摘要一致性单测：登录写在线会话与强退写黑名单都依赖
 * {@code md5(token)}，两侧实现必须逐字节一致，否则强退黑名单永远命不中原会话。
 */
class QaTokenDigestTest {

    @Test
    @DisplayName("MD5 摘要值与 JDK 标准实现一致（已知向量 abc）")
    void shouldMatchKnownDigestVector() {
        // RFC 1321 标准测试向量："abc" -> 900150983cd24fb0d6963f7d28e17f72
        assertThat(TokenDenyInterceptor.md5("abc")).isEqualTo("900150983cd24fb0d6963f7d28e17f72");
        assertThat(OnlineUserService.md5("abc")).isEqualTo("900150983cd24fb0d6963f7d28e17f72");
    }

    @Test
    @DisplayName("两侧 MD5 实现对任意 token 完全一致（含中文/空串/长串）")
    void shouldProduceIdenticalDigestsOnBothSides() throws Exception {
        String[] tokens = {"", "Bearer-ish token with spaces", "中文令牌支持UTF8",
                "x".repeat(4096), "eyJhbGciOiJIUzI1NiJ9.payload.sig"};
        for (String token : tokens) {
            String expected = toHex(MessageDigest.getInstance("MD5")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
            assertThat(OnlineUserService.md5(token))
                    .as("OnlineUserService.md5(%s…)", token)
                    .isEqualTo(expected);
            assertThat(TokenDenyInterceptor.md5(token))
                    .as("TokenDenyInterceptor.md5(%s…)", token)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("MD5 输出固定为 32 位小写十六进制")
    void shouldReturn32LowercaseHex() {
        String digest = OnlineUserService.md5("any-token");
        assertThat(digest).hasSize(32).matches("[0-9a-f]{32}");
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

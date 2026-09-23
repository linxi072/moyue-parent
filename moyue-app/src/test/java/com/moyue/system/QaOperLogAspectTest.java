package com.moyue.system;

import com.moyue.system.aspect.OperLogAspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OperLogAspect 截断逻辑单测（反射直测私有方法）：
 * 验收点 4 要求 param / result 截断 2000，且 URL 截断 255。
 */
class QaOperLogAspectTest {

    private String invokeTruncate(int max, String text) throws Exception {
        OperLogAspect aspect = new OperLogAspect();
        Method m = OperLogAspect.class.getDeclaredMethod("truncate", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(aspect, text, max);
    }

    @Test
    @DisplayName("短文本原样返回（不截断）")
    void shouldKeepShortText() throws Exception {
        String text = "a".repeat(1999);
        assertThat(invokeTruncate(2000, text)).isSameAs(text);
    }

    @Test
    @DisplayName("恰 2000 字符不截断，2001 字符截断到 2000")
    void shouldTruncateAtBoundary() throws Exception {
        String exact = "b".repeat(2000);
        assertThat(invokeTruncate(2000, exact)).hasSize(2000).isSameAs(exact);

        String over = "c".repeat(2001);
        assertThat(invokeTruncate(2000, over)).hasSize(2000);
    }

    @Test
    @DisplayName("超长文本截断后长度严格 = 2000（TEXT 字段安全）")
    void shouldTruncateHugeTextTo2000() throws Exception {
        String huge = "记".repeat(100_000);
        String result = invokeTruncate(2000, huge);
        assertThat(result).hasSize(2000);
        // 前缀保持
        assertThat(result).startsWith("记记记");
    }

    @Test
    @DisplayName("null 输入返回 null（不 NPE）")
    void shouldReturnNullOnNullInput() throws Exception {
        assertThat(invokeTruncate(2000, null)).isNull();
    }

    @Test
    @DisplayName("URL 截断上限 255（VARCHAR(255) 字段安全）")
    void shouldTruncateUrlTo255() throws Exception {
        String longUrl = "/api/v1/admin/system/" + "p".repeat(400);
        assertThat(invokeTruncate(255, longUrl)).hasSize(255);
    }

    @Test
    @DisplayName("切面常量 MAX_TEXT_LENGTH = 2000（与 V14 TEXT 截断约定一致）")
    void maxTextLengthConstantShouldBe2000() throws Exception {
        var f = OperLogAspect.class.getDeclaredField("MAX_TEXT_LENGTH");
        f.setAccessible(true);
        assertThat(f.get(null)).isEqualTo(2000);
    }
}

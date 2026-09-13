package com.moyue.system;

import com.moyue.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GenService 表名白名单（SQL 注入防线）单测。
 *
 * <p>validateTableName 为 private，通过反射直测：
 * 仅 [A-Za-z0-9_]{1,64} 放行，一切含引号 / 空格 / 分号 / 注释符的输入必须被拒，
 * 从而保证 information_schema 查询的表名永远走参数化占位且内容受控。</p>
 */
class QaGenServiceWhitelistTest {

    private final Object genService = new com.moyue.system.service.gen.GenService();

    private void validate(String tableName) throws Exception {
        Method m = genService.getClass().getDeclaredMethod("validateTableName", String.class);
        m.setAccessible(true);
        try {
            m.invoke(genService, tableName);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 反射包装：还原业务异常类型
            if (e.getCause() instanceof BizException) {
                throw (BizException) e.getCause();
            }
            throw e;
        }
    }

    @Test
    @DisplayName("合法表名（字母数字下划线，≤64）应放行")
    void shouldAcceptValidTableNames() throws Exception {
        validate("sys_user");
        validate("sys_dict_type");
        validate("t1");
        validate("T_2_3");
        // 64 字符边界
        validate("a".repeat(64));
    }

    @ParameterizedTest(name = "[{index}] 拒绝: {0}")
    @ValueSource(strings = {
            "user; DROP TABLE sys_user;--",
            "user' OR '1'='1",
            "user\" UNION SELECT",
            "sys user",          // 空格
            "sys-user",          // 连字符
            "sys.user",          // 点号（跨库探测）
            "user/*comment*/",
            "user\ndrop",        // 换行
            "",                  // 空串
    })
    @DisplayName("注入/非法字符表名必须被拒（BizException）")
    void shouldRejectInjectionAttempts(String bad) {
        assertThatThrownBy(() -> validate(bad)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("null表名必须被拒")
    void shouldRejectNull() {
        assertThatThrownBy(() -> validate(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("超过 64 字符的表名必须被拒（边界+1）")
    void shouldRejectOver64() {
        assertThatThrownBy(() -> validate("a".repeat(65))).isInstanceOf(BizException.class);
    }
}

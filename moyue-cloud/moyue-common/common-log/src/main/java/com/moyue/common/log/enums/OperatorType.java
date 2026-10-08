package com.moyue.common.log.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作人类别。
 *
 * <p>与 {@code sys_user.user_type} 对齐：1 读者 / 2 作家 / 3 运营，另设 0 其它、4 系统内部。
 *
 * @author moyue
 */
@Getter
@AllArgsConstructor
public enum OperatorType {

    OTHER(0, "其它"),
    READER(1, "读者"),
    AUTHOR(2, "作家"),
    MANAGE(3, "后台运营"),
    SYSTEM(4, "系统内部"),
    ;

    private final int value;
    private final String label;

    public static OperatorType of(Integer value) {
        if (value == null) {
            return OTHER;
        }
        for (OperatorType t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        return OTHER;
    }
}

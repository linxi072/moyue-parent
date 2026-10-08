package com.moyue.common.log.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作类型。
 *
 * <p>对应 moyue-system 审计日志三域中「操作日志」的 business_type 字段。
 *
 * @author moyue
 */
@Getter
@AllArgsConstructor
public enum BusinessType {

    /** 其它 */
    OTHER(0, "其它"),
    /** 新增 */
    INSERT(1, "新增"),
    /** 修改 */
    UPDATE(2, "修改"),
    /** 删除 */
    DELETE(3, "删除"),
    /** 授权 */
    GRANT(4, "授权"),
    /** 导出 */
    EXPORT(5, "导出"),
    /** 导入 */
    IMPORT(6, "导入"),
    /** 强退 */
    FORCE(7, "强退"),
    /** 生成代码 */
    GENCODE(8, "生成代码"),
    /** 清空 */
    CLEAN(9, "清空"),
    /** 审核 */
    AUDIT(10, "审核"),
    /** 上线/下线 */
    PUBLISH(11, "上下线"),
    ;

    private final int value;
    private final String label;

    public static BusinessType of(Integer value) {
        if (value == null) {
            return OTHER;
        }
        for (BusinessType t : values()) {
            if (t.value == value) {
                return t;
            }
        }
        return OTHER;
    }
}

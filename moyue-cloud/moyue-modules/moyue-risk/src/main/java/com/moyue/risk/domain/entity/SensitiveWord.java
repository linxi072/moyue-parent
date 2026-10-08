package com.moyue.risk.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 敏感词实体（风控域）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_sensitive_word")
public class SensitiveWord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 敏感词（唯一） */
    private String word;

    /** 级别：1 拦截 / 2 告警 */
    private Integer level;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;

    /** 累计命中次数 */
    private Integer hitCount;

    // 级别常量
    public static final int LEVEL_BLOCK = 1;
    public static final int LEVEL_WARN = 2;

    // 启用常量
    public static final int ENABLED_ON = 1;
    public static final int ENABLED_OFF = 0;
}

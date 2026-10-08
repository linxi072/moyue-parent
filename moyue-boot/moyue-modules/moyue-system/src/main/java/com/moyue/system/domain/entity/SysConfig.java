package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 参数配置表实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 参数名称 */
    private String configName;

    /** 参数键名，唯一 */
    private String configKey;

    /** 参数键值 */
    private String configValue;

    /** 类型：0 自定义 / 1 系统内置（不可删除） */
    private Integer configType;
}

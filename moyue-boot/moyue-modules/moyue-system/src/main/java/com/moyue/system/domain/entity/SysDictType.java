package com.moyue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 字典类型表实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字典名称 */
    private String dictName;

    /** 字典类型标识，唯一 */
    private String dictType;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}

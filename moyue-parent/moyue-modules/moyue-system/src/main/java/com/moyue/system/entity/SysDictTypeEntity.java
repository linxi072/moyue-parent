package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型实体，映射 sys_dict_type 表（V14）。
 */
@Data
@TableName("sys_dict_type")
public class SysDictTypeEntity {

    /** 字典类型主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 字典名称，如 小说状态 */
    private String dictName;

    /** 字典类型编码，如 novel_status */
    private String dictType;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    @TableField("is_deleted")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

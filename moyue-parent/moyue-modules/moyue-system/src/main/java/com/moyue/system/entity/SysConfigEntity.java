package com.moyue.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数配置实体，映射 sys_config 表（V14）。
 * <p>{@code isSystem=1} 为内置参数，不允许删除。</p>
 */
@Data
@TableName("sys_config")
public class SysConfigEntity {

    /** 参数主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 参数名称，如 默认章节字数下限 */
    private String configName;

    /** 参数键名，如 system.chapter.min-word-count */
    private String configKey;

    /** 参数键值 */
    private String configValue;

    /** 是否内置参数：0 否 / 1 是（内置不可删除） */
    private Integer isSystem;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

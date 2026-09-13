package com.moyue.risk.sensitive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体，映射 sensitive_word 表（V13）。
 * <p>注意：全局逻辑删除未启用（对齐项目现状），is_deleted 由业务代码自行维护，
 * 查询需显式过滤 {@code is_deleted = 0}。</p>
 * <p>P2-15：hit_count 列由 V15 迁移新增（V13 无该列），记录该词被机审命中的累计次数。</p>
 */
@Data
@TableName("sensitive_word")
public class SensitiveWordEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 敏感词 */
    private String word;

    /** 等级：1 拦截 / 2 告警（转人工） */
    private Integer level;

    /** 分类：政治/广告/谩骂/涉黄… */
    private String category;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 命中次数统计（V15 新增列） */
    private Integer hitCount;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

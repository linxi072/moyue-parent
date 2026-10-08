package com.moyue.search.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 搜索屏蔽词实体（拦截 / 告警分级）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_search_block_word")
public class BlockWord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 屏蔽词（唯一） */
    private String word;

    /** 级别：1 拦截 / 2 告警 */
    private Integer level;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;

    /** 命中次数 */
    private Integer hitCount;
}

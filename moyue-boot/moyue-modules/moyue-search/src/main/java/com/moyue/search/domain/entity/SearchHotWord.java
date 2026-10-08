package com.moyue.search.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 搜索热词实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_search_hot_word")
public class SearchHotWord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 热词 */
    private String word;

    /** 命中次数 */
    private Integer hitCount;

    /** 权重（排序用） */
    private Integer weight;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}

package com.moyue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI 配额实体（AI 域）：按用户记录 token 配额与消耗。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_ai_quota")
public class AiQuota extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID（唯一） */
    private Long userId;

    /** 配额总额（token） */
    private Integer total;

    /** 已用（token） */
    private Integer used;

    /** 剩余（token） */
    private Integer remain;
}

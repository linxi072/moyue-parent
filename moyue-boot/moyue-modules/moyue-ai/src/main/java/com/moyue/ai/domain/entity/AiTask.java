package com.moyue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * AI 任务实体（AI 域）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_ai_task")
public class AiTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务类型：1 续写 / 2 润色 / 3 摘要 / 4 大纲 */
    private Integer taskType;

    /** 提示词 */
    private String prompt;

    /** 模型标识 */
    private String model;

    /** 状态：0 待处理 / 1 成功 / 2 失败 */
    private Integer status;

    /** 生成结果 */
    private String result;

    /** 消耗 token 数 */
    private Integer costTokens;

    /** 发起用户 */
    private Long userId;
}

package com.moyue.ai.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 任务视图。
 *
 * @author moyue
 */
@Data
@Builder
public class AiTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer taskType;
    private String prompt;
    private String model;
    private Integer status;
    private String result;
    private Integer costTokens;
    private Long userId;
    private LocalDateTime createTime;
    private String remark;
}

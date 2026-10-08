package com.moyue.ai.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 配额视图。
 *
 * @author moyue
 */
@Data
@Builder
public class AiQuotaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Integer total;
    private Integer used;
    private Integer remain;
    private LocalDateTime createTime;
    private String remark;
}

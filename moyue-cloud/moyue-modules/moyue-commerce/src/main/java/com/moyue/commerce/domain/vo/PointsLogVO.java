package com.moyue.commerce.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分流水视图。
 *
 * @author moyue
 */
@Data
@Builder
public class PointsLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Integer bizType;
    private BigDecimal changeAmount;
    private BigDecimal balanceAfter;
    private String refId;
    private String remark;
    private LocalDateTime createTime;
}

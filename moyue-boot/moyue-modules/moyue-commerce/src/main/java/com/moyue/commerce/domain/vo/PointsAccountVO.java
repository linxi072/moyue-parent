package com.moyue.commerce.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户积分账户视图。
 *
 * @author moyue
 */
@Data
@Builder
public class PointsAccountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private BigDecimal balance;
    private BigDecimal totalIncome;
    private BigDecimal totalConsume;
    private BigDecimal frozen;
    private LocalDateTime createTime;
    private String remark;
}

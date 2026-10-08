package com.moyue.commerce.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付费订单视图。
 *
 * @author moyue
 */
@Data
@Builder
public class PayOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private String productName;
    private Integer productType;
    private BigDecimal amount;
    private Integer quantity;
    private Integer payChannel;
    private Integer status;
    private String tradeNo;
    private LocalDateTime createTime;
    private String remark;
}

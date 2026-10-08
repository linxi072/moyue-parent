package com.moyue.commerce.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分兑换商品视图。
 *
 * @author moyue
 */
@Data
@Builder
public class ProductVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer type;
    private BigDecimal priceAmount;
    private Integer points;
    private Integer stock;
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
    private String remark;
}

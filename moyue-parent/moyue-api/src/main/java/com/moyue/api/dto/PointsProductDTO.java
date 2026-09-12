package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 积分商品数据传输对象（跨服务共享）。
 */
@Data
public class PointsProductDTO implements Serializable {

    private Long id;

    private String name;

    private String description;

    private String imageUrl;

    /** 兑换所需积分 */
    private Integer costPoints;

    private Integer stock;

    /** 1 上架 / 2 下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

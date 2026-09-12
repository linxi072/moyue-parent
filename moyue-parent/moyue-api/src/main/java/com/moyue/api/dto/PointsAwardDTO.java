package com.moyue.api.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 积分发放请求 DTO（服务间内部调用，生产者 → moyue-commerce /internal/points/award）。
 * bizType：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费。
 */
@Data
public class PointsAwardDTO implements Serializable {

    private Long userId;

    private Integer bizType;

    private Integer points;

    private String remark;
}

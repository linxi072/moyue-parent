package com.moyue.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 积分账户数据传输对象（跨服务共享）。每用户一行。
 */
@Data
public class PointsAccountDTO implements Serializable {

    private Long userId;

    /** 当前积分余额 */
    private Integer balance;

    /** 累计获得 */
    private Integer totalEarned;

    /** 累计消费 */
    private Integer totalSpent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

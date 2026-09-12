package com.moyue.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分账户实体，映射 points_account 表（每用户一行）。
 * user_id 为自然主键，值等于调用方传入的 userId，使用 INPUT 策略由业务写入。
 */
@Data
@TableName("points_account")
public class PointsAccountEntity {

    /** 用户 ID（自然主键，等于调用方传入的 userId） */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 当前积分余额 */
    private Integer balance;

    /** 累计获得 */
    private Integer totalEarned;

    /** 累计消费 */
    private Integer totalSpent;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

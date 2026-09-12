package com.moyue.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日签到实体，映射 points_check_in 表。
 * uk_user_date 保证一人一天一条：并发重复签到靠唯一键冲突识别。
 */
@Data
@TableName("points_check_in")
public class PointsCheckInEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID → user.id */
    private Long userId;

    /** 签到日期 */
    private LocalDate checkInDate;

    /** 本次发放积分 */
    private Integer points;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 签到时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

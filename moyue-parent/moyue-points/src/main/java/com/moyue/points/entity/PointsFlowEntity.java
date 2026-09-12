package com.moyue.points.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水实体，映射 points_flow 表（只增台账）。
 * biz_type：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费。
 */
@Data
@TableName("points_flow")
public class PointsFlowEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID → user.id */
    private Long userId;

    /** 业务类型 */
    private Integer bizType;

    /** 积分变动（正获得 / 负消费） */
    private Integer points;

    /** 备注 */
    private String remark;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 发生时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

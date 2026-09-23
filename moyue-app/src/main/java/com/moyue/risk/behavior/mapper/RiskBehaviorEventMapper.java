package com.moyue.risk.behavior.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.behavior.entity.RiskBehaviorEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 用户行为事件 Mapper（不可变追加日志） */
@Mapper
public interface RiskBehaviorEventMapper extends BaseMapper<RiskBehaviorEventEntity> {

    /** 同设备窗口内去重用户数（同设备多账号识别） */
    @Select("SELECT COUNT(DISTINCT user_id) FROM risk_behavior_event "
            + "WHERE device_id = #{deviceId} AND create_time >= #{since}")
    long countDistinctUsersByDevice(@Param("deviceId") String deviceId,
                                     @Param("since") LocalDateTime since);

    /** 同用户窗口内去重设备数（盗号识别） */
    @Select("SELECT COUNT(DISTINCT device_id) FROM risk_behavior_event "
            + "WHERE user_id = #{userId} AND create_time >= #{since}")
    long countDistinctDevicesByUser(@Param("userId") Long userId,
                                     @Param("since") LocalDateTime since);

    /** 同用户指定类型窗口内事件数（积分异常识别） */
    @Select("SELECT COUNT(*) FROM risk_behavior_event "
            + "WHERE user_id = #{userId} AND event_type = #{eventType} AND create_time >= #{since}")
    long countByUserAndType(@Param("userId") Long userId,
                            @Param("eventType") String eventType,
                            @Param("since") LocalDateTime since);
}

package com.moyue.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.points.entity.PointsCheckInEntity;
import org.apache.ibatis.annotations.Mapper;

/** 签到记录 Mapper */
@Mapper
public interface PointsCheckInMapper extends BaseMapper<PointsCheckInEntity> {
}

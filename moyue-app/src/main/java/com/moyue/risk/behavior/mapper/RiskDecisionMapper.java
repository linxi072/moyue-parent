package com.moyue.risk.behavior.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 风控决策 / 处置记录 Mapper（不可变追加日志） */
@Mapper
public interface RiskDecisionMapper extends BaseMapper<RiskDecisionEntity> {
}

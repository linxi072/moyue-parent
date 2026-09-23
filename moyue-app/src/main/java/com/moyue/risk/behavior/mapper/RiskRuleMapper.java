package com.moyue.risk.behavior.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.behavior.entity.RiskRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/** 风控规则配置 Mapper */
@Mapper
public interface RiskRuleMapper extends BaseMapper<RiskRuleEntity> {
}

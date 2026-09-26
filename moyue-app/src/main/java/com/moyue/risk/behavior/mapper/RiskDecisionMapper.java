package com.moyue.risk.behavior.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 风控决策 / 处置记录 Mapper（不可变追加日志） */
@Mapper
public interface RiskDecisionMapper extends BaseMapper<RiskDecisionEntity> {

    /** 按命中规则编码聚合决策数（看板指标用）。H2 / MySQL 下列名标签大小写不一，指标聚合侧做兼容取值 */
    @Select("SELECT rule_code, COUNT(*) AS cnt FROM risk_decision GROUP BY rule_code")
    List<Map<String, Object>> countByRuleCode();
}

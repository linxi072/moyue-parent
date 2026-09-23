package com.moyue.risk.behavior.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.risk.behavior.entity.RiskBehaviorEventEntity;
import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.entity.RiskRuleEntity;
import com.moyue.risk.behavior.mapper.RiskDecisionMapper;
import com.moyue.risk.behavior.mapper.RiskRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 行为风控业务门面（P2-C）：提交行为（采集 + 规则评估）/ 规则配置（增改、开关热刷）/ 决策查询。
 * 规则配置化、降级不阻断主链路（通知失败仅告警）。
 */
@Service
public class BehaviorRiskService {

    @Autowired
    private BehaviorEventCollector collector;

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private RiskRuleMapper ruleMapper;

    @Autowired
    private RiskDecisionMapper decisionMapper;

    /**
     * 提交一次行为：采集事件 → 运行规则引擎 → 返回命中决策。
     * 采集与评估在同一事务内（同连接），事件对引擎立即可见。
     */
    @Transactional
    public List<RiskDecisionEntity> submit(Long userId, String deviceId, String eventType, Long bizId, String ip) {
        RiskBehaviorEventEntity e = collector.collect(userId, deviceId, eventType, bizId, ip);
        return ruleEngine.evaluate(e);
    }

    /** 规则列表（未删除，按编码升序） */
    public List<RiskRuleEntity> listRules() {
        LambdaQueryWrapper<RiskRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskRuleEntity::getIsDeleted, 0).orderByAsc(RiskRuleEntity::getRuleCode);
        return ruleMapper.selectList(wrapper);
    }

    /** 新增 / 更新规则（按 ruleCode 存在则改、不存在则插） */
    @Transactional
    public RiskRuleEntity saveRule(RiskRuleEntity rule) {
        LambdaQueryWrapper<RiskRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskRuleEntity::getRuleCode, rule.getRuleCode())
                .eq(RiskRuleEntity::getIsDeleted, 0).last("LIMIT 1");
        RiskRuleEntity exist = ruleMapper.selectOne(wrapper);
        if (exist != null) {
            exist.setRuleName(rule.getRuleName());
            exist.setRuleType(rule.getRuleType());
            exist.setEnabled(rule.getEnabled());
            exist.setAction(rule.getAction());
            exist.setConfigJson(rule.getConfigJson());
            exist.setUpdateTime(LocalDateTime.now());
            ruleMapper.updateById(exist);
            return exist;
        }
        rule.setIsDeleted(0);
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.insert(rule);
        return rule;
    }

    /** 启用 / 停用规则（配置热刷） */
    @Transactional
    public void setEnabled(String ruleCode, boolean enabled) {
        LambdaQueryWrapper<RiskRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskRuleEntity::getRuleCode, ruleCode)
                .eq(RiskRuleEntity::getIsDeleted, 0).last("LIMIT 1");
        RiskRuleEntity exist = ruleMapper.selectOne(wrapper);
        if (exist == null) {
            return;
        }
        exist.setEnabled(enabled ? 1 : 0);
        exist.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(exist);
    }

    /** 某用户风控决策记录（按时间倒序） */
    public List<RiskDecisionEntity> listDecisions(Long userId) {
        LambdaQueryWrapper<RiskDecisionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskDecisionEntity::getUserId, userId).orderByDesc(RiskDecisionEntity::getCreateTime);
        return decisionMapper.selectList(wrapper);
    }
}

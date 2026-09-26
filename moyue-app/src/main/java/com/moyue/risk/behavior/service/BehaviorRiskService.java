package com.moyue.risk.behavior.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.risk.behavior.entity.RiskBehaviorEventEntity;
import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.entity.RiskRuleEntity;
import com.moyue.risk.behavior.mapper.RiskBehaviorEventMapper;
import com.moyue.risk.behavior.mapper.RiskDecisionMapper;
import com.moyue.risk.behavior.mapper.RiskRuleMapper;
import com.moyue.risk.behavior.model.RiskMetricsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private RiskBehaviorEventMapper eventMapper;

    /**
     * 提交一次行为：采集事件 → 运行规则引擎 → 返回命中决策。
     * 独立事务（REQUIRES_NEW）：即便调用方主链路回滚，风控审计与决策仍落库，
     * 保证「刷分/盗号」等异常行为有迹可查。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<RiskDecisionEntity> submit(Long userId, String deviceId, String eventType, Long bizId, String ip) {
        RiskBehaviorEventEntity e = collector.collect(userId, deviceId, eventType, bizId, ip);
        return ruleEngine.evaluate(e);
    }

    /**
     * 业务动作前置预检（P2-C 拦截闭环）：以「本次若发生」的视角评估既有行为，
     * 命中 BLOCK 级规则即应拦截该动作（如刷分兑换）。仅读取、不落库、不通知。
     *
     * @return 命中的决策列表（未落盘）；无命中返回空列表
     */
    public List<RiskDecisionEntity> preCheck(Long userId, String deviceId, String eventType, String ip) {
        RiskBehaviorEventEntity e = new RiskBehaviorEventEntity();
        e.setUserId(userId);
        e.setDeviceId(deviceId);
        e.setEventType(eventType);
        e.setIp(ip);
        return ruleEngine.evaluatePreCheck(e);
    }

    /**
     * 行为风控看板指标（P2-C）：事件总量 / 决策总量 / 各处置结论计数 / 按规则分组。
     */
    public RiskMetricsVO metrics() {
        RiskMetricsVO vo = new RiskMetricsVO();
        vo.setTotalEvents(eventMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setTotalDecisions(decisionMapper.selectCount(new LambdaQueryWrapper<>()));
        vo.setBlockCount(decisionMapper.selectCount(new LambdaQueryWrapper<RiskDecisionEntity>().eq(RiskDecisionEntity::getDecision, "BLOCK")));
        vo.setReviewCount(decisionMapper.selectCount(new LambdaQueryWrapper<RiskDecisionEntity>().eq(RiskDecisionEntity::getDecision, "REVIEW")));
        vo.setPassCount(decisionMapper.selectCount(new LambdaQueryWrapper<RiskDecisionEntity>().eq(RiskDecisionEntity::getDecision, "PASS")));
        Map<String, Long> byRuleCode = new HashMap<>();
        for (Map<String, Object> row : decisionMapper.countByRuleCode()) {
            // 兼容 H2（rule_code 小写标签）/ MySQL（RULE_CODE 大写标签）的列名差异
            Object code = row.get("rule_code");
            if (code == null) {
                code = row.get("RULE_CODE");
            }
            if (code == null) {
                code = row.get("ruleCode");
            }
            Object cnt = row.get("cnt");
            byRuleCode.put(code == null ? "null" : String.valueOf(code), ((Number) cnt).longValue());
        }
        vo.setByRuleCode(byRuleCode);
        return vo;
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

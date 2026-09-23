package com.moyue.risk.behavior.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.api.message.client.MessageDispatchClient;
import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.risk.behavior.entity.RiskBehaviorEventEntity;
import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.entity.RiskRuleEntity;
import com.moyue.risk.behavior.mapper.RiskBehaviorEventMapper;
import com.moyue.risk.behavior.mapper.RiskDecisionMapper;
import com.moyue.risk.behavior.mapper.RiskRuleMapper;
import com.moyue.risk.behavior.model.RuleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置化行为风控规则引擎（P2-C 核心）。
 *
 * <p>对一次行为事件，遍历所有启用规则，按类型评估：</p>
 * <ul>
 *   <li>FREQ：窗口内同用户同类型行为次数 &gt; 阈值 → 频控命中（刷量/刷分）</li>
 *   <li>DEVICE_MULTI_ACCOUNT：窗口内同设备去重用户数 &gt; 阈值 → 同设备多账号</li>
 *   <li>POINTS_ANOMALY：窗口内同用户 REDEEM(积分兑换) 次数 &gt; 阈值 → 积分异常</li>
 *   <li>ACCOUNT_THEFT：窗口内同用户去重设备数 &gt; 阈值 → 盗号（异地/多设备）</li>
 * </ul>
 * 命中 → 落 risk_decision（REVIEW/BLOCK）并复用触达链路 {@link MessageDispatchClient} 通知风控负责人；
 * 通知失败仅告警、不阻断主链路（配置驱动降级）。
 */
@Slf4j
@Service
public class RuleEngine {

    /** 风控负责人用户 ID（通知接收人；生产可改为配置） */
    private static final long RISK_OWNER_USER_ID = 1L;

    @Autowired
    private RiskBehaviorEventMapper eventMapper;

    @Autowired
    private RiskRuleMapper ruleMapper;

    @Autowired
    private RiskDecisionMapper decisionMapper;

    @Autowired
    private MessageDispatchClient messageDispatchClient;

    /** 对一次事件评估全部启用规则，返回本次命中的决策列表（无命中返回空） */
    public List<RiskDecisionEntity> evaluate(RiskBehaviorEventEntity event) {
        List<RiskDecisionEntity> decisions = new ArrayList<>();
        for (RiskRuleEntity rule : enabledRules()) {
            RuleConfig cfg = RuleConfig.parse(rule.getConfigJson());
            RuleHit hit = evaluateRule(rule, cfg, event);
            if (hit.violated) {
                RiskDecisionEntity d = persistDecision(event, rule, hit);
                decisions.add(d);
                notifyIfNeeded(rule, d, event);
            }
        }
        return decisions;
    }

    // ---------------------------------------------------------------
    // 规则评估
    // ---------------------------------------------------------------

    private RuleHit evaluateRule(RiskRuleEntity rule, RuleConfig cfg, RiskBehaviorEventEntity event) {
        String type = rule.getRuleType();
        LocalDateTime since = LocalDateTime.now().minusMinutes(cfg.getWindowMinutes());
        switch (type) {
            case "FREQ": {
                long count = eventMapper.selectCount(new LambdaQueryWrapper<RiskBehaviorEventEntity>()
                        .eq(RiskBehaviorEventEntity::getUserId, event.getUserId())
                        .eq(RiskBehaviorEventEntity::getEventType, event.getEventType())
                        .ge(RiskBehaviorEventEntity::getCreateTime, since));
                // 采集已在同事务内落库，selectCount 已含本次事件，直接用 count 判定
                long total = count;
                if (total > cfg.getThreshold()) {
                    return new RuleHit(true, "窗口 " + cfg.getWindowMinutes() + "min 内同类型行为 " + total
                            + " 次 > 阈值 " + cfg.getThreshold());
                }
                return RuleHit.NONE;
            }
            case "DEVICE_MULTI_ACCOUNT": {
                long distinctUsers = eventMapper.countDistinctUsersByDevice(event.getDeviceId(), since);
                if (distinctUsers > cfg.getThreshold()) {
                    return new RuleHit(true, "窗口内同设备去重用户 " + distinctUsers
                            + " > 阈值 " + cfg.getThreshold());
                }
                return RuleHit.NONE;
            }
            case "POINTS_ANOMALY": {
                long redeemCount = eventMapper.countByUserAndType(event.getUserId(), "REDEEM", since);
                // 采集已在同事务内落库，count 已含本次事件，直接判定
                long total = redeemCount;
                if (total > cfg.getThreshold()) {
                    return new RuleHit(true, "窗口内积分兑换 " + total
                            + " 次 > 阈值 " + cfg.getThreshold());
                }
                return RuleHit.NONE;
            }
            case "ACCOUNT_THEFT": {
                long distinctDevices = eventMapper.countDistinctDevicesByUser(event.getUserId(), since);
                if (distinctDevices > cfg.getThreshold()) {
                    return new RuleHit(true, "窗口内同用户去重设备 " + distinctDevices
                            + " > 阈值 " + cfg.getThreshold());
                }
                return RuleHit.NONE;
            }
            default:
                return RuleHit.NONE;
        }
    }

    // ---------------------------------------------------------------
    // 决策落库 + 通知（降级不阻断）
    // ---------------------------------------------------------------

    private RiskDecisionEntity persistDecision(RiskBehaviorEventEntity event, RiskRuleEntity rule, RuleHit hit) {
        boolean block = "BLOCK".equals(rule.getAction());
        RiskDecisionEntity d = new RiskDecisionEntity();
        d.setUserId(event.getUserId());
        d.setDeviceId(event.getDeviceId());
        d.setRuleCode(rule.getRuleCode());
        d.setEventType(event.getEventType());
        d.setDecision(block ? "BLOCK" : "REVIEW");
        d.setRiskLevel(block ? 3 : 2);
        d.setMessage(rule.getRuleName() + "：" + hit.message);
        d.setCreateTime(LocalDateTime.now());
        decisionMapper.insert(d);
        return d;
    }

    private void notifyIfNeeded(RiskRuleEntity rule, RiskDecisionEntity d, RiskBehaviorEventEntity event) {
        try {
            MessageDispatchDTO dto = new MessageDispatchDTO();
            dto.setUserId(RISK_OWNER_USER_ID);
            dto.setTemplateCode("RISK_DECISION_ALERT");
            dto.setBizType("RISK");
            dto.setBizId(event.getId());
            Map<String, String> params = new HashMap<>();
            params.put("rule", rule.getRuleName());
            params.put("user", String.valueOf(event.getUserId()));
            params.put("decision", d.getDecision());
            dto.setParams(params);
            messageDispatchClient.dispatch(dto);
        } catch (Exception ex) {
            log.warn("[behavior-risk] 风控通知发送失败（不阻断主链路）：rule={}, err={}",
                    rule.getRuleCode(), ex.getMessage());
        }
    }

    private List<RiskRuleEntity> enabledRules() {
        LambdaQueryWrapper<RiskRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskRuleEntity::getEnabled, 1)
                .eq(RiskRuleEntity::getIsDeleted, 0);
        return ruleMapper.selectList(wrapper);
    }

    /** 单条规则的命中结果（不可变） */
    private static final class RuleHit {
        static final RuleHit NONE = new RuleHit(false, "");
        final boolean violated;
        final String message;

        RuleHit(boolean violated, String message) {
            this.violated = violated;
            this.message = message;
        }
    }
}

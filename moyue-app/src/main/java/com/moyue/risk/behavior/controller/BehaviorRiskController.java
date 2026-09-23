package com.moyue.risk.behavior.controller;

import com.moyue.common.R;
import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.entity.RiskRuleEntity;
import com.moyue.risk.behavior.service.BehaviorRiskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 行为风控接口：行为采集 + 评估 / 规则列表 / 规则增改与开关热刷 / 决策查询。
 * 路径前缀 /api/v1 与网关路由保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class BehaviorRiskController {

    @Autowired
    private BehaviorRiskService behaviorRiskService;

    /** 提交一次行为事件采集 + 风控评估，返回命中决策 */
    @PostMapping("/risk/behavior/collect")
    public R<List<RiskDecisionEntity>> collect(@RequestBody CollectRequest req) {
        return R.ok(behaviorRiskService.submit(req.getUserId(), req.getDeviceId(),
                req.getEventType(), req.getBizId(), req.getIp()));
    }

    /** 规则列表 */
    @GetMapping("/risk/behavior/rules")
    public R<List<RiskRuleEntity>> listRules() {
        return R.ok(behaviorRiskService.listRules());
    }

    /** 新增 / 更新规则 */
    @PostMapping("/risk/behavior/rules")
    public R<RiskRuleEntity> saveRule(@RequestBody RiskRuleEntity rule) {
        return R.ok(behaviorRiskService.saveRule(rule));
    }

    /** 启用 / 停用规则（配置热刷） */
    @PostMapping("/risk/behavior/rules/{code}/toggle")
    public R<Void> toggle(@PathVariable String code, @RequestParam boolean enabled) {
        behaviorRiskService.setEnabled(code, enabled);
        return R.ok();
    }

    /** 某用户风控决策记录 */
    @GetMapping("/risk/behavior/decisions")
    public R<List<RiskDecisionEntity>> decisions(@RequestParam Long userId) {
        return R.ok(behaviorRiskService.listDecisions(userId));
    }

    /** 行为采集请求体 */
    public static class CollectRequest {
        private Long userId;
        private String deviceId;
        private String eventType;
        private Long bizId;
        private String ip;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public Long getBizId() {
            return bizId;
        }

        public void setBizId(Long bizId) {
            this.bizId = bizId;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }
    }
}

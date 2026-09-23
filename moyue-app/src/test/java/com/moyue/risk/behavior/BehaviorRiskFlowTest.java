package com.moyue.risk.behavior;

import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.service.BehaviorRiskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行为风控链路集成测试（P2-C）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表（含 V24 三表）。
 * 覆盖四类配置化规则：频控 / 同设备多账号 / 积分异常 / 盗号，以及阈值内不命中。
 */
@SpringBootTest
@ActiveProfiles("test")
class BehaviorRiskFlowTest {

    @Autowired
    private BehaviorRiskService behaviorRiskService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM risk_behavior_event");
        jdbcTemplate.update("DELETE FROM risk_decision");
        jdbcTemplate.update("DELETE FROM risk_rule");
        jdbcTemplate.update(
                "INSERT INTO risk_rule (id, rule_code, rule_name, rule_type, enabled, action, config_json, is_deleted, create_time, update_time) VALUES "
                        + "(9001001, 'R_FREQ', '登录频控', 'FREQ', 1, 'BLOCK', '{\"windowMinutes\":60,\"threshold\":2}', 0, NOW(), NOW()),"
                        + "(9001002, 'R_DEVICE', '同设备多账号', 'DEVICE_MULTI_ACCOUNT', 1, 'REVIEW', '{\"windowMinutes\":60,\"threshold\":2}', 0, NOW(), NOW()),"
                        + "(9001003, 'R_POINTS', '积分异常', 'POINTS_ANOMALY', 1, 'REVIEW', '{\"windowMinutes\":60,\"threshold\":2}', 0, NOW(), NOW()),"
                        + "(9001004, 'R_THEFT', '盗号', 'ACCOUNT_THEFT', 1, 'BLOCK', '{\"windowMinutes\":60,\"threshold\":2}', 0, NOW(), NOW())");
    }

    @Test
    void freqRule_blocksBurstLogin() {
        behaviorRiskService.submit(9001L, "devA", "LOGIN", null, "1.1.1.1");
        behaviorRiskService.submit(9001L, "devA", "LOGIN", null, "1.1.1.1");
        List<RiskDecisionEntity> hits = behaviorRiskService.submit(9001L, "devA", "LOGIN", null, "1.1.1.1");
        assertThat(hits).anyMatch(d -> "R_FREQ".equals(d.getRuleCode()) && "BLOCK".equals(d.getDecision()));
    }

    @Test
    void deviceMultiAccount_detected() {
        behaviorRiskService.submit(9101L, "devX", "LOGIN", null, "2.2.2.2");
        behaviorRiskService.submit(9102L, "devX", "LOGIN", null, "2.2.2.2");
        List<RiskDecisionEntity> hits = behaviorRiskService.submit(9103L, "devX", "LOGIN", null, "2.2.2.2");
        assertThat(hits).anyMatch(d -> "R_DEVICE".equals(d.getRuleCode()) && "REVIEW".equals(d.getDecision()));
    }

    @Test
    void pointsAnomaly_detectedOnRedeem() {
        behaviorRiskService.submit(9201L, "devP", "REDEEM", 5001L, "3.3.3.3");
        behaviorRiskService.submit(9201L, "devP", "REDEEM", 5002L, "3.3.3.3");
        List<RiskDecisionEntity> hits = behaviorRiskService.submit(9201L, "devP", "REDEEM", 5003L, "3.3.3.3");
        assertThat(hits).anyMatch(d -> "R_POINTS".equals(d.getRuleCode()));
    }

    @Test
    void accountTheft_detectedOnMultiDevice() {
        behaviorRiskService.submit(9301L, "dev1", "LOGIN", null, "4.4.4.1");
        behaviorRiskService.submit(9301L, "dev2", "LOGIN", null, "4.4.4.2");
        List<RiskDecisionEntity> hits = behaviorRiskService.submit(9301L, "dev3", "LOGIN", null, "4.4.4.3");
        assertThat(hits).anyMatch(d -> "R_THEFT".equals(d.getRuleCode()) && "BLOCK".equals(d.getDecision()));
    }

    @Test
    void noViolationBelowThreshold() {
        List<RiskDecisionEntity> hits = behaviorRiskService.submit(9401L, "devS", "LOGIN", null, "5.5.5.5");
        assertThat(hits).isEmpty();
    }
}

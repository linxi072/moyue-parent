package com.moyue.risk.behavior.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

/**
 * 风控规则参数（由 risk_rule.config_json 反序列化）。
 * 不同规则类型复用同一结构，按类型解释字段：
 * <ul>
 *   <li>FREQ / POINTS_ANOMALY：windowMinutes 统计窗口、threshold 阈值（严格大于即命中）</li>
 *   <li>DEVICE_MULTI_ACCOUNT / ACCOUNT_THEFT：windowMinutes 统计窗口、threshold 去重对象数阈值</li>
 * </ul>
 * 解析失败时回退默认值，保证配置异常不阻断规则引擎。
 */
@Data
public class RuleConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 统计窗口（分钟） */
    private int windowMinutes = 60;

    /** 阈值（严格大于即命中） */
    private int threshold = 3;

    /** 反序列化；非法 JSON 回退默认配置 */
    public static RuleConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return new RuleConfig();
        }
        try {
            RuleConfig cfg = MAPPER.readValue(json, RuleConfig.class);
            if (cfg.getWindowMinutes() <= 0) {
                cfg.setWindowMinutes(60);
            }
            if (cfg.getThreshold() <= 0) {
                cfg.setThreshold(3);
            }
            return cfg;
        } catch (Exception e) {
            return new RuleConfig();
        }
    }
}

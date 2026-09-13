package com.moyue.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel 网关规则配置（P2-17）。
 *
 * <p>因本地无 Sentinel Dashboard，规则以 <b>Java Bean 编程式定义</b>：网关启动时经
 * {@link GatewayRuleManager#loadRules(Set)} / {@link DegradeRuleManager#loadRules(java.util.Collection)}
 * 注册路由级限流与熔断规则；并设置自定义 {@link GatewayBlockHandler} 输出 {@code R<T>}（HTTP 200）。</p>
 *
 * <p>本类为 T01 骨架（P2-17 要求在 T05 统一细修，含阈值外置与登录接口按 IP 限流）；
 * 当前默认阈值为「每路由 100 QPS 限流 + 慢调用 RT 熔断」。</p>
 */
@Configuration
public class SentinelGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelGatewayConfig.class);

    /** 默认每路由 QPS 上限（P2-17 将改为可配 moyue.sentinel.*） */
    private static final double DEFAULT_ROUTE_QPS = 100.0;

    /** 熔断：慢调用 RT 阈值（ms） */
    private static final double SLOW_REQUEST_RT_MS = 2000.0;

    /** 熔断：时间窗口（秒） */
    private static final int DEGRADE_TIME_WINDOW_SEC = 10;

    /** 熔断：最小请求数（低于此值不触发熔断） */
    private static final int DEGRADE_MIN_REQUEST_AMOUNT = 5;

    /**
     * 需要纳管的网关路由 id（与 {@code application.yml} 中 spring.cloud.gateway.routes[].id 对齐）。
     * 以路由 id 作为 Sentinel 资源名，实现「按路由」QPS 限流。
     */
    private static final String[] ROUTE_IDS = {
            "moyue-auth", "moyue-user",
            "moyue-book", "moyue-book-files", "moyue-read", "moyue-chapter", "moyue-search",
            "moyue-comment", "moyue-blog", "moyue-message", "moyue-im",
            "moyue-author", "moyue-points", "moyue-merch",
            "moyue-audit", "moyue-operation", "moyue-stat", "moyue-reward",
            "moyue-system", "moyue-system-login",
            "moyue-ai", "moyue-risk", "moyue-risk-admin"
    };

    @PostConstruct
    public void init() {
        initBlockHandler();
        initGatewayFlowRules();
        initDegradeRules();
        log.info("[Sentinel] 网关规则已加载：路由级限流 {} 条（{} QPS/路由），熔断 {} 条",
                ROUTE_IDS.length, DEFAULT_ROUTE_QPS, ROUTE_IDS.length);
    }

    /** 自定义拦截响应体：统一输出 R&lt;T&gt;（HTTP 200） */
    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler(new GatewayBlockHandler());
    }

    /** 路由级 QPS 限流规则：以网关路由 id 为资源名 */
    private void initGatewayFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();
        for (String routeId : ROUTE_IDS) {
            GatewayFlowRule rule = new GatewayFlowRule(routeId);
            rule.setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(DEFAULT_ROUTE_QPS);
            rule.setIntervalSec(1);
            rules.add(rule);
        }
        GatewayRuleManager.loadRules(rules);
    }

    /** 慢调用 RT 熔断规则：同样以路由 id 为资源名 */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        for (String routeId : ROUTE_IDS) {
            DegradeRule rule = new DegradeRule(routeId);
            rule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
            rule.setCount(SLOW_REQUEST_RT_MS);
            rule.setTimeWindow(DEGRADE_TIME_WINDOW_SEC);
            rule.setMinRequestAmount(DEGRADE_MIN_REQUEST_AMOUNT);
            rule.setStatIntervalMs(1000);
            rules.add(rule);
        }
        DegradeRuleManager.loadRules(rules);
    }
}

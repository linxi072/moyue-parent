package com.moyue.api.risk.client;

import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.service.BehaviorRiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 行为风控进程内客户端（P2-C 闭环补全）。
 *
 * <p>把「一次用户行为事件」交给风控规则引擎评估，对主业务流<b>非阻断</b>：
 * 风控服务未就绪 / 数据库抖动 / 规则异常时仅告警并吞掉异常，绝不向上抛出，
 * 保证不回滚、不阻断登录 / 签到 / 兑换 / 打赏等主链路。</p>
 *
 * <p>埋点方（auth / points / operation）统一以 {@code @Autowired(required = false)} 注入本客户端，
 * 在业务成功路径末尾调用 {@link #collect}，无需关心风控是否可用。</p>
 */
@Slf4j
@Component
public class BehaviorRiskClient {

    @Autowired(required = false)
    private BehaviorRiskService behaviorRiskService;

    /**
     * 采集并评估一次行为事件（非阻断语义：异步采集的等价物）。
     *
     * @param userId    行为用户 ID（必填，规则引擎核心维度）
     * @param deviceId  设备指纹 / 设备 ID（最佳努力：登录可从 {@code X-Device-Id} 头取，其余流程可为 null）
     * @param eventType 事件类型：LOGIN / SIGN_IN / REDEEM / REWARD / PUBLISH
     * @param bizId     关联业务主键（订单 / 章节等，可为 null）
     * @param ip        来源 IP（最佳努力，可为 null）
     * @return 本次命中的决策列表；引擎未就绪或异常时返回空列表（不抛异常）
     */
    public List<RiskDecisionEntity> collect(Long userId, String deviceId, String eventType, Long bizId, String ip) {
        if (behaviorRiskService == null) {
            return Collections.emptyList();
        }
        try {
            return behaviorRiskService.submit(userId, deviceId, eventType, bizId, ip);
        } catch (Exception ex) {
            log.warn("[behavior-risk] 行为事件采集/评估失败（已降级，不阻断主链路）：userId={}, eventType={}, err={}",
                    userId, eventType, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 业务动作前置风控预检（P2-C 拦截闭环）：仅基于<b>既有</b>行为事件评估是否命中 BLOCK 级规则，
     * 不写入本次行为事件（避免重复埋点）；返回 true 表示应拦截本次动作。
     * 风控服务未就绪 / 异常时返回 false（降级：不阻断主链路）。
     *
     * @return true=命中 BLOCK 应拦截；false=放行或风控不可用
     */
    public boolean preCheck(Long userId, String deviceId, String eventType, String ip) {
        if (behaviorRiskService == null) {
            return false;
        }
        try {
            List<RiskDecisionEntity> decisions = behaviorRiskService.preCheck(userId, deviceId, eventType, ip);
            return decisions != null && decisions.stream().anyMatch(d -> "BLOCK".equals(d.getDecision()));
        } catch (Exception ex) {
            log.warn("[behavior-risk] 风控预检失败（已降级，不阻断主链路）：userId={}, eventType={}, err={}",
                    userId, eventType, ex.getMessage());
            return false;
        }
    }
}

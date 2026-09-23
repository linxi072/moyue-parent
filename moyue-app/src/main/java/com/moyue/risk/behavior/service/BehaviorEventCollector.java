package com.moyue.risk.behavior.service;

import com.moyue.risk.behavior.entity.RiskBehaviorEventEntity;
import com.moyue.risk.behavior.mapper.RiskBehaviorEventMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 行为事件采集器（P2-C）。将一次用户行为（登录 / 签到 / 兑换 / 打赏 / 发布）追加写入
 * risk_behavior_event，供规则引擎评估。事件日志仅追加、不可变，不做逻辑删除。
 */
@Service
public class BehaviorEventCollector {

    @Autowired
    private RiskBehaviorEventMapper eventMapper;

    /**
     * 采集一次行为事件并落库。
     *
     * @return 落库后的事件实体（含自增主键），供规则引擎评估
     */
    public RiskBehaviorEventEntity collect(Long userId, String deviceId, String eventType,
                                           Long bizId, String ip) {
        RiskBehaviorEventEntity e = new RiskBehaviorEventEntity();
        e.setUserId(userId);
        e.setDeviceId(deviceId);
        e.setEventType(eventType);
        e.setBizId(bizId);
        e.setIp(ip);
        e.setRiskScore(0);
        e.setCreateTime(LocalDateTime.now());
        eventMapper.insert(e);
        return e;
    }
}

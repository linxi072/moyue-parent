package com.moyue.api.risk.client;

import com.moyue.risk.behavior.entity.RiskDecisionEntity;
import com.moyue.risk.behavior.service.BehaviorRiskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 行为风控客户端契约测试（P2-C 闭环补全）。
 * 不依赖 Spring，验证「透传决策」与「服务异常/未就绪时非阻断降级」两条核心契约。
 */
@ExtendWith(MockitoExtension.class)
class BehaviorRiskClientTest {

    @Mock
    private BehaviorRiskService behaviorRiskService;

    @InjectMocks
    private BehaviorRiskClient client;

    @Test
    void collect_passesThroughDecisions() {
        RiskDecisionEntity d = new RiskDecisionEntity();
        when(behaviorRiskService.submit(9001L, "devA", "LOGIN", null, "1.1.1.1"))
                .thenReturn(List.of(d));

        List<RiskDecisionEntity> result = client.collect(9001L, "devA", "LOGIN", null, "1.1.1.1");

        assertThat(result).containsExactly(d);
    }

    @Test
    void collect_serviceThrows_returnsEmptyAndDoesNotPropagate() {
        when(behaviorRiskService.submit(9001L, null, "REDEEM", 1L, null))
                .thenThrow(new RuntimeException("db down"));

        List<RiskDecisionEntity> result = client.collect(9001L, null, "REDEEM", 1L, null);

        // 异常被吞掉，返回空列表且不向上抛出
        assertThat(result).isEmpty();
    }
}

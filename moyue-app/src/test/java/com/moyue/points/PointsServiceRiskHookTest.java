package com.moyue.points;

import com.moyue.api.risk.client.BehaviorRiskClient;
import com.moyue.points.service.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 积分域行为风控埋点集成测试（P2-C 闭环补全）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；以 {@code @MockBean BehaviorRiskClient} 隔离真实引擎，
 * 验证「业务成功路径确实触发埋点」与「风控客户端抛异常时主链路不回滚」。
 * 建表 / 数据源约定同 {@code PointsOrderFlowTest}。
 */
@SpringBootTest
@ActiveProfiles("test")
class PointsServiceRiskHookTest {

    @Autowired
    private PointsService pointsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private BehaviorRiskClient behaviorRiskClient;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM points_order WHERE product_id IN (9001)");
        jdbcTemplate.update("DELETE FROM points_account WHERE user_id IN (7001)");
        jdbcTemplate.update("DELETE FROM points_check_in WHERE user_id IN (7001)");
        jdbcTemplate.update("DELETE FROM points_product WHERE id IN (9001)");
        jdbcTemplate.update("INSERT INTO points_product (id, name, cost_points, stock, status) VALUES (9001, '风控埋点商品', 100, 5, 1)");
        jdbcTemplate.update("INSERT INTO points_account (user_id, balance, total_earned, total_spent) VALUES (7001, 500, 500, 0)");
    }

    @Test
    void createOrder_collectsRedeemEvent() {
        pointsService.createOrder(7001L, 9001L);

        verify(behaviorRiskClient, times(1)).collect(eq(7001L), any(), eq("REDEEM"), any(), any());
        assertThat(queryInt("SELECT COUNT(*) FROM points_order WHERE user_id = 7001 AND product_id = 9001")).isEqualTo(1);
    }

    @Test
    void checkIn_collectsSignInEvent() {
        pointsService.checkIn(7001L);

        verify(behaviorRiskClient, times(1)).collect(eq(7001L), any(), eq("SIGN_IN"), any(), any());
    }

    @Test
    void createOrder_stillSucceedsWhenRiskClientThrows() {
        doThrow(new RuntimeException("risk down")).when(behaviorRiskClient)
                .collect(any(), any(), any(), any(), any());

        pointsService.createOrder(7001L, 9001L);

        // 主链路不回滚：订单仍落库、积分已扣减
        assertThat(queryInt("SELECT COUNT(*) FROM points_order WHERE user_id = 7001 AND product_id = 9001")).isEqualTo(1);
        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = 7001")).isEqualTo(400);
    }

    private int queryInt(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}

package com.moyue.points;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 积分兑换链路集成测试（P0-2 核心靶标）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表，无 Mock 数据层。
 * 覆盖：兑换成功（余额扣减 / 库存扣减 / 订单落库联动）、积分不足（含无账户用户）、商品下架、库存售罄。
 * 校验基线：业务码 0 成功 / 10001 参数 / 20001 资源不存在；HTTP 统一 200 承载。
 *
 * 合并说明：moyue-points 并入 moyue-commerce 后，本测试由 moyue-commerce 的
 * CommerceApplication 承载（包路径不在其祖先目录，故显式指定 classes）。
 * profile 固定为 {@code test}，数据源/ Flyway 配置见 {@code src/test/resources/application-test.yml}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointsOrderFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM points_order WHERE product_id IN (9001, 9002, 9003, 9004)");
        jdbcTemplate.update("DELETE FROM points_account WHERE user_id IN (7001, 7002, 7003)");
        jdbcTemplate.update("DELETE FROM points_product WHERE id IN (9001, 9002, 9003, 9004)");
        jdbcTemplate.update("INSERT INTO points_product (id, name, cost_points, stock, status) VALUES (9001, '测试商品A', 100, 5, 1)");
        jdbcTemplate.update("INSERT INTO points_product (id, name, cost_points, stock, status) VALUES (9002, '测试商品B', 100, 3, 1)");
        jdbcTemplate.update("INSERT INTO points_product (id, name, cost_points, stock, status) VALUES (9003, '已下架商品', 100, 3, 2)");
        jdbcTemplate.update("INSERT INTO points_product (id, name, cost_points, stock, status) VALUES (9004, '售罄商品', 100, 0, 1)");
        jdbcTemplate.update("INSERT INTO points_account (user_id, balance, total_earned, total_spent) VALUES (7001, 500, 500, 0)");
        jdbcTemplate.update("INSERT INTO points_account (user_id, balance, total_earned, total_spent) VALUES (7003, 50, 50, 0)");
    }

    @Test
    void createOrder_success_deductsBalanceAndStockAndInsertsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/points/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001,\"productId\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.costPoints").value(100))
                .andExpect(jsonPath("$.data.productName").value("测试商品A"));

        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = 7001")).isEqualTo(400);
        assertThat(queryInt("SELECT total_spent FROM points_account WHERE user_id = 7001")).isEqualTo(100);
        assertThat(queryInt("SELECT stock FROM points_product WHERE id = 9001")).isEqualTo(4);
        assertThat(queryInt("SELECT COUNT(*) FROM points_order WHERE user_id = 7001 AND product_id = 9001")).isEqualTo(1);
    }

    @Test
    void createOrder_insufficientBalance_rejectedAndNoStockDeducted() throws Exception {
        mockMvc.perform(post("/api/v1/points/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7003,\"productId\":9002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("积分不足"));

        // 失败路径不得扣库存、不得落订单
        assertThat(queryInt("SELECT stock FROM points_product WHERE id = 9002")).isEqualTo(3);
        assertThat(queryInt("SELECT COUNT(*) FROM points_order WHERE user_id = 7003")).isEqualTo(0);
    }

    @Test
    void createOrder_userWithoutAccount_rejectedAndNoSideEffect() throws Exception {
        // 7002 无账户：兑换事务内先懒建户（余额 0），随后条件扣减影响 0 行 → 抛「积分不足」，
        // 该异常使整个 @Transactional 回滚。因此失败路径必须零副作用：
        // 账户行 / 库存 / 订单都不留痕（懒建户只是为了让条件扣减这条 SQL 有落点，不是业务产物）。
        mockMvc.perform(post("/api/v1/points/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7002,\"productId\":9001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("积分不足"));

        assertThat(queryInt("SELECT COUNT(*) FROM points_account WHERE user_id = 7002")).isZero();
        assertThat(queryInt("SELECT stock FROM points_product WHERE id = 9001")).isEqualTo(5);
        assertThat(queryInt("SELECT COUNT(*) FROM points_order WHERE user_id = 7002")).isZero();
    }

    @Test
    void getAccount_userWithoutAccount_autoCreatesZeroBalance() throws Exception {
        // 账户懒初始化走的是独立只读接口（非事务，无回滚），不存在则建户并返回余额 0
        mockMvc.perform(get("/api/v1/points/accounts/{userId}", 7002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(7002))
                .andExpect(jsonPath("$.data.balance").value(0));

        assertThat(queryInt("SELECT COUNT(*) FROM points_account WHERE user_id = 7002")).isEqualTo(1);
        assertThat(queryInt("SELECT balance FROM points_account WHERE user_id = 7002")).isZero();
    }

    @Test
    void createOrder_offShelfProduct_resourceNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/points/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001,\"productId\":9003}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void createOrder_soldOutProduct_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/points/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7001,\"productId\":9004}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("商品库存不足"));
    }

    private Integer queryInt(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}

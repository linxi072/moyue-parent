package com.moyue.merch;

import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.merch.entity.MerchOrderEntity;
import com.moyue.merch.service.MerchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 商城结算链路集成测试（P0-3 靶标）：库存扣减的<b>并发防超卖</b>是本用例的核心价值。
 *
 * <p>{@code MerchService.checkout} 的库存扣减是数据库层条件原子更新
 * （{@code stock = stock - qty WHERE id = ? AND status = 1 AND stock >= qty}），
 * 以 UPDATE 影响行数判定成败。本测试用真实 H2 + 真实事务 + 多线程并发结算，
 * 证明「库存 3 / 8 个买家并发下单」最终只有 3 单成功、库存不为负——
 * 这类结论靠 mock 永远验证不出来。
 *
 * <p>H2 内存库（MySQL 兼容模式）+ Flyway 全量建表，无 Mock 数据层。
 * profile 固定 {@code test}，数据源与 Flyway 配置见 {@code src/test/resources/application-test.yml}。
 */
@SpringBootTest
@ActiveProfiles("test")
class MerchCheckoutFlowTest {

    private static final long PRODUCT_ID = 8001L;

    /** 并发用例的买家 id 起始值，避免与其它用例互相干扰 */
    private static final long CONCURRENT_USER_BASE = 8100L;

    @Autowired
    private MerchService merchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM merch_cart WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM merch_order WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM merch_product WHERE id = ?", PRODUCT_ID);
        jdbcTemplate.update("INSERT INTO merch_product "
                        + "(id, name, description, price, stock, sales, status, is_deleted, create_time, update_time) "
                        + "VALUES (?, '瑕疵周边·测试品', 'P0-3 测试商品', 19.90, 3, 0, 1, 0, NOW(), NOW())",
                PRODUCT_ID);
    }

    /** 给指定用户塞一行购物车（id 显式给出：H2 schema 的 id 列是 BIGINT NOT NULL 且无自增） */
    private void seedCart(long userId, int quantity) {
        jdbcTemplate.update("INSERT INTO merch_cart "
                        + "(id, user_id, product_id, quantity, is_deleted, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, 0, NOW(), NOW())",
                userId * 10, userId, PRODUCT_ID, quantity);
    }

    private Integer queryInt(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private BigDecimal queryDecimal(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }

    // --------------------------------------------------------------- 结算正常 / 失败路径

    @Test
    @DisplayName("结算成功：按购物车数量扣库存、累加销量、落订单、清空购物车（逻辑删除）")
    void checkout_success_deductsStockAddsSalesInsertsOrderAndClearsCart() {
        seedCart(8010L, 2);

        List<MerchOrderEntity> orders = merchService.checkout(8010L);

        assertThat(orders).hasSize(1);
        MerchOrderEntity order = orders.get(0);
        assertThat(order.getUserId()).isEqualTo(8010L);
        assertThat(order.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getStatus()).isZero();
        assertThat(order.getOrderNo()).isNotBlank();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("39.80");

        // 库存 3 - 2 = 1，销量 0 + 2 = 2
        assertThat(queryInt("SELECT stock FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(1);
        assertThat(queryInt("SELECT sales FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(2);
        assertThat(queryInt("SELECT COUNT(*) FROM merch_order WHERE product_id = ?", PRODUCT_ID)).isEqualTo(1);

        // 购物车是「逻辑删除」而非物理删除：行还在、is_deleted 置 1
        assertThat(queryInt("SELECT COUNT(*) FROM merch_cart WHERE product_id = ?", PRODUCT_ID)).isEqualTo(1);
        assertThat(queryInt("SELECT is_deleted FROM merch_cart WHERE product_id = ?", PRODUCT_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("结算失败（库存不足）：整单回滚——库存不变、销量不变、不落订单、购物车行仍有效")
    void checkout_insufficientStock_rolledBackWithNoSideEffect() {
        seedCart(8011L, 5); // 需求 5 > 库存 3

        Throwable t = catchThrowable(() -> merchService.checkout(8011L));

        assertThat(t).isInstanceOf(BizException.class);
        assertThat(t.getMessage()).contains("库存不足");
        // 条件更新影响 0 行即抛异常，@Transactional 整体回滚：全链路零副作用
        assertThat(queryInt("SELECT stock FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(3);
        assertThat(queryInt("SELECT sales FROM merch_product WHERE id = ?", PRODUCT_ID)).isZero();
        assertThat(queryInt("SELECT COUNT(*) FROM merch_order WHERE product_id = ?", PRODUCT_ID)).isZero();
        assertThat(queryInt("SELECT is_deleted FROM merch_cart WHERE product_id = ?", PRODUCT_ID)).isZero();
    }

    @Test
    @DisplayName("结算失败（购物车为空）：PARAM_ERROR「购物车为空」，不动库存")
    void checkout_emptyCart_rejected() {
        assertThatThrownBy(() -> merchService.checkout(8012L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("购物车为空");

        assertThat(queryInt("SELECT stock FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(3);
        assertThat(queryInt("SELECT COUNT(*) FROM merch_order WHERE product_id = ?", PRODUCT_ID)).isZero();
    }

    @Test
    @DisplayName("结算失败（购物车含已下架商品）：PARAM_ERROR 提示移除，不动库存")
    void checkout_offShelfProductInCart_rejected() {
        seedCart(8013L, 1);
        jdbcTemplate.update("UPDATE merch_product SET status = 2 WHERE id = ?", PRODUCT_ID);

        assertThatThrownBy(() -> merchService.checkout(8013L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已下架");

        assertThat(queryInt("SELECT stock FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(3);
        assertThat(queryInt("SELECT COUNT(*) FROM merch_order WHERE product_id = ?", PRODUCT_ID)).isZero();
    }

    // --------------------------------------------------------------- 并发防超卖（核心）

    @Test
    @DisplayName("并发结算防超卖：库存 3 面对 8 个并发买家，恰好 3 单成功、库存归零不为负")
    void checkout_concurrent_noOversell() throws Exception {
        int buyers = 8;
        int initialStock = 3;
        for (int i = 0; i < buyers; i++) {
            seedCart(CONCURRENT_USER_BASE + i, 1);
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            long userId = CONCURRENT_USER_BASE + i;
            Callable<Throwable> task = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                    merchService.checkout(userId);
                    return null; // 成功
                } catch (Throwable t) {
                    return t; // 失败：把异常带出来，由主线程统一判定
                }
            };
            futures.add(pool.submit(task));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("所有并发线程应已就绪").isTrue();
        start.countDown(); // 同一起跑线，最大化真实竞争
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("并发结算应在 30s 内结束").isTrue();

        int succeeded = 0;
        for (Future<Throwable> f : futures) {
            Throwable t = f.get();
            if (t == null) {
                succeeded++;
                continue;
            }
            // 失败必须由「库存不足」业务条件拒绝导致，而不是 H2 锁超时等基础设施异常——
            // 否则说明条件更新没起到兜底作用，超卖风险真实存在
            assertThat(t)
                    .as("并发失败只能是被条件更新挡住（库存不足），不得是其它异常：%s", t)
                    .isInstanceOf(BizException.class);
            assertThat(t.getMessage()).contains("库存不足");
        }

        assertThat(succeeded).as("库存 %s 只能支撑 %s 单", initialStock, initialStock).isEqualTo(initialStock);
        assertThat(queryInt("SELECT stock FROM merch_product WHERE id = ?", PRODUCT_ID))
                .as("库存必须归零且绝不为负")
                .isZero();
        assertThat(queryInt("SELECT sales FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(initialStock);
        assertThat(queryInt("SELECT COUNT(*) FROM merch_order WHERE product_id = ?", PRODUCT_ID))
                .as("落库订单数必须等于成功数")
                .isEqualTo(initialStock);
        assertThat(queryInt("SELECT COUNT(*) FROM merch_cart WHERE product_id = ? AND is_deleted = 0", PRODUCT_ID))
                .as("未成功的买家购物车行必须仍然有效")
                .isEqualTo(buyers - initialStock);
    }

    // --------------------------------------------------------------- 支付幂等

    @Test
    @DisplayName("支付幂等：首次 0→1 已支付，重复支付不报错也不改变状态")
    void pay_isIdempotent_repeatPayKeepsPaid() {
        seedCart(8014L, 1);
        List<MerchOrderEntity> orders = merchService.checkout(8014L);
        String orderNo = orders.get(0).getOrderNo();

        List<MerchOrderEntity> paid = merchService.pay(orderNo, 8014L);
        assertThat(paid).hasSize(1);
        assertThat(paid.get(0).getStatus()).isEqualTo(1);

        // 重复支付：条件更新 WHERE status = 0 影响 0 行，但接口按幂等语义返回成功且状态仍为 1
        List<MerchOrderEntity> paidAgain = merchService.pay(orderNo, 8014L);
        assertThat(paidAgain).hasSize(1);
        assertThat(paidAgain.get(0).getStatus()).isEqualTo(1);
        assertThat(queryInt("SELECT status FROM merch_order WHERE order_no = ?", orderNo)).isEqualTo(1);
        // 支付不得二次扣库存
        assertThat(queryInt("SELECT stock FROM merch_product WHERE id = ?", PRODUCT_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("支付校验归属：订单不属于当前用户时 RESOURCE_NOT_FOUND，订单状态不变")
    void pay_wrongUser_resourceNotFound() {
        seedCart(8015L, 1);
        List<MerchOrderEntity> orders = merchService.checkout(8015L);
        String orderNo = orders.get(0).getOrderNo();

        Throwable t = catchThrowable(() -> merchService.pay(orderNo, 9999L));

        assertThat(t).isInstanceOf(BizException.class);
        assertThat(((BizException) t).getCode()).isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(queryInt("SELECT status FROM merch_order WHERE order_no = ?", orderNo)).isZero();
    }

    @Test
    @DisplayName("结算落库金额口径：total_amount = 商品单价 × 数量，保留两位小数")
    void checkout_totalAmountIsPriceTimesQuantity() {
        jdbcTemplate.update("UPDATE merch_product SET stock = 10, price = 12.34 WHERE id = ?", PRODUCT_ID);
        seedCart(8016L, 3);

        List<MerchOrderEntity> orders = merchService.checkout(8016L);
        String orderNo = orders.get(0).getOrderNo();

        BigDecimal storedPrice = queryDecimal("SELECT price FROM merch_product WHERE id = ?", PRODUCT_ID);
        BigDecimal expected = storedPrice.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);
        assertThat(queryDecimal("SELECT total_amount FROM merch_order WHERE order_no = ?", orderNo))
                .isEqualByComparingTo(expected);
        assertThat(expected).isEqualByComparingTo("37.02");
    }
}

package com.moyue.merch;

import com.moyue.merch.entity.MerchOrderEntity;
import com.moyue.merch.service.MerchService;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会员折扣接入商城结算的集成测试（续开发 P2-B 权益闭环）。
 *
 * <p>H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；会员折扣经 {@code MemberClient → MemberService}
 * 真实链路生效（支付网关默认 Stub，{@code subscribe} 即可开通生效中会员）。</p>
 *
 * <p>覆盖：会员生效中下单按折扣率下浮、折扣随数量缩放、非会员原价结算不受影响。
 * 会员服务异常/降级由 {@code MerchService.resolveDiscountRate} 兜底为原价，本用例不验证异常分支
 * （异常分支已在 {@code MemberClient} 的 try/catch 中保证），仅验证正常与边界金额口径。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MerchCheckoutDiscountTest {

    private static final long PRODUCT_ID = 9001L;
    private static final long MEMBER_USER = 9001L;
    private static final long NORMAL_USER = 9002L;
    private static final String TIER_CODE = "TEST_BASIC";

    @Autowired
    private MerchService merchService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        // 幂等清理本用例数据（与 MerchCheckoutFlowTest / MemberSubscribeFlowTest 用户与商品 ID 隔离）
        jdbcTemplate.update("DELETE FROM merch_cart WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM merch_order WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM merch_product WHERE id = ?", PRODUCT_ID);
        jdbcTemplate.update("DELETE FROM member_subscription WHERE user_id IN (?, ?)", MEMBER_USER, NORMAL_USER);
        jdbcTemplate.update("DELETE FROM member_tier WHERE tier_code = ?", TIER_CODE);

        jdbcTemplate.update("INSERT INTO merch_product "
                        + "(id, name, description, price, stock, sales, status, is_deleted, create_time, update_time) "
                        + "VALUES (?, '折扣测试周边', '会员折扣用例', 100.00, 10, 0, 1, 0, NOW(), NOW())",
                PRODUCT_ID);
        jdbcTemplate.update("INSERT INTO member_tier "
                        + "(id, tier_code, tier_name, monthly_price, duration_days, ad_free, discount_rate, badge, sort, is_deleted, create_time, update_time) "
                        + "VALUES (9001001, ?, '测试基础包', 15.00, 30, 1, 0.90, 'basic', 1, 0, NOW(), NOW())",
                TIER_CODE);
        // MEMBER_USER 开通生效中会员（支付网关默认 Stub 成功，折扣率 0.90）
        memberService.subscribe(MEMBER_USER, TIER_CODE);
    }

    private void seedCart(long userId, int quantity) {
        jdbcTemplate.update("INSERT INTO merch_cart "
                        + "(id, user_id, product_id, quantity, is_deleted, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, 0, NOW(), NOW())",
                userId * 10, userId, PRODUCT_ID, quantity);
    }

    private BigDecimal queryDecimal(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }

    @Test
    @DisplayName("会员生效中结算：本行金额按折扣率 0.90 下浮（100 × 1 = 90.00）")
    void checkout_memberActive_appliesDiscount() {
        seedCart(MEMBER_USER, 1);

        List<MerchOrderEntity> orders = merchService.checkout(MEMBER_USER);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo("90.00");
        assertThat(queryDecimal("SELECT total_amount FROM merch_order WHERE order_no = ?", orders.get(0).getOrderNo()))
                .isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("会员折扣随数量缩放：100 × 3 × 0.90 = 270.00")
    void checkout_memberActive_discountScalesWithQuantity() {
        seedCart(MEMBER_USER, 3);

        List<MerchOrderEntity> orders = merchService.checkout(MEMBER_USER);

        assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo("270.00");
    }

    @Test
    @DisplayName("非会员结算：原价（100 × 2 = 200.00），不受会员模块影响")
    void checkout_nonMember_paysFullPrice() {
        seedCart(NORMAL_USER, 2);

        List<MerchOrderEntity> orders = merchService.checkout(NORMAL_USER);

        assertThat(orders.get(0).getTotalAmount()).isEqualByComparingTo("200.00");
    }
}

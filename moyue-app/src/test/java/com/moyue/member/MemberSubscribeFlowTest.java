package com.moyue.member;

import com.moyue.common.BizException;
import com.moyue.member.entity.MemberSubscriptionEntity;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会员订阅链路集成测试（P2-B）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表（含 V23 member_tier / member_subscription）。
 * 覆盖：开通即激活并发放权益、到期自动降级收回权益、未知套餐拒绝、订阅历史落库。
 */
@SpringBootTest
@ActiveProfiles("test")
class MemberSubscribeFlowTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM member_subscription WHERE user_id = 8001");
        jdbcTemplate.update("DELETE FROM member_tier WHERE tier_code = 'MONTHLY_BASIC'");
        jdbcTemplate.update(
                "INSERT INTO member_tier (id, tier_code, tier_name, monthly_price, duration_days, "
                        + "ad_free, discount_rate, badge, sort, is_deleted, create_time, update_time) "
                        + "VALUES (8001001, 'MONTHLY_BASIC', '基础包月', 15.00, 30, 1, 0.90, 'basic', 1, 0, NOW(), NOW())");
    }

    @Test
    void subscribe_activatesAndGrantsBenefits() {
        MemberSubscriptionEntity sub = memberService.subscribe(8001L, "MONTHLY_BASIC");
        assertThat(sub.getStatus()).isEqualTo(1); // STATUS_ACTIVE
        assertThat(sub.getEndTime()).isAfterOrEqualTo(sub.getStartTime());

        MemberService.MemberBenefits benefits = memberService.getBenefits(8001L);
        assertThat(benefits.isActive()).isTrue();
        assertThat(benefits.isAdFree()).isTrue();
        assertThat(benefits.getDiscountRate()).isEqualByComparingTo("0.90");
        assertThat(benefits.getBadges()).contains("basic");
    }

    @Test
    void expiredSubscription_dropsBenefits() {
        memberService.subscribe(8001L, "MONTHLY_BASIC");
        // 人为把有效期拨到过去，触发到期降级
        jdbcTemplate.update("UPDATE member_subscription SET end_time = DATEADD(DAY, -1, NOW()) WHERE user_id = 8001");
        memberService.syncExpired();

        MemberService.MemberBenefits benefits = memberService.getBenefits(8001L);
        assertThat(benefits.isActive()).isFalse();
        assertThat(benefits.isAdFree()).isFalse();
        // 历史仍在（逻辑未删除）
        assertThat(memberService.listSubscriptions(8001L)).hasSize(1);
        assertThat(memberService.listSubscriptions(8001L).get(0).getStatus()).isEqualTo(2); // EXPIRED
    }

    @Test
    void subscribe_unknownTier_rejected() {
        assertThatThrownBy(() -> memberService.subscribe(8001L, "NOPE"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void subscriptionHistory_returnsOneRow() {
        memberService.subscribe(8001L, "MONTHLY_BASIC");
        assertThat(memberService.listSubscriptions(8001L)).hasSize(1);
    }
}

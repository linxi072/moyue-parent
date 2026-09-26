package com.moyue.member;

import com.moyue.api.member.client.MemberClient;
import com.moyue.common.R;
import com.moyue.member.client.MemberPaymentGateway;
import com.moyue.member.entity.MemberSubscriptionEntity;
import com.moyue.member.mapper.MemberSubscriptionMapper;
import com.moyue.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 会员连续订阅（自动续费）集成测试（P2-B，H2 内存库 + Flyway 全量建表）。
 * ① 续费成功路径：endTime 延长一个周期、renewAt 前移、renewLastStatus=0、failCount 清零。
 * ② 续费失败路径（网关返回 failed）：保持 active 不延长、renewLastStatus=1、failCount++、不抛异常。
 */
@SpringBootTest
@ActiveProfiles("test")
class MemberRenewalFlowTest {

    private static final long USER = 9001L;
    private static final String TIER = "RENEW_TIER";

    @MockBean
    private MemberPaymentGateway paymentGateway;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberClient memberClient;

    @Autowired
    private MemberSubscriptionMapper subscriptionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        // 默认 mock 网关：真实成功（realSuccess=true），覆盖 ① 成功路径
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(MemberPaymentGateway.ChargeResult.success("MOCK-SERIAL", "mock"));

        jdbcTemplate.update("DELETE FROM member_subscription WHERE user_id = ?", USER);
        jdbcTemplate.update("DELETE FROM member_tier WHERE tier_code = ?", TIER);
        jdbcTemplate.update(
                "INSERT INTO member_tier (id, tier_code, tier_name, monthly_price, duration_days, "
                        + "ad_free, discount_rate, badge, sort, is_deleted, create_time, update_time) "
                        + "VALUES (9002001, ?, '续费测试包', 20.00, 30, 1, 0.90, 'r', 1, 0, NOW(), NOW())",
                TIER);
    }

    private long seedActiveSub(long id, LocalDateTime endTime, LocalDateTime renewAt) {
        jdbcTemplate.update(
                "INSERT INTO member_subscription (id, user_id, tier_code, tier_name, status, start_time, end_time, "
                        + "order_no, pay_serial, channel, is_deleted, auto_renew, renew_cycle_days, renew_at, "
                        + "renew_fail_count, renew_last_status, create_time, update_time) "
                        + "VALUES (?, ?, ?, '续费测试包', 1, ?, ?, 'ORD', 'PS', 'stub', 0, 1, 30, ?, 0, NULL, NOW(), NOW())",
                id, USER, TIER, Timestamp.valueOf(endTime.minusDays(5)), Timestamp.valueOf(endTime), Timestamp.valueOf(renewAt));
        return id;
    }

    @Test
    void renewal_success_extendsEndTime() {
        // H2(MySQL 兼容模式) 对 DATETIME 做纳秒截断，seed 值需 withNano(0) 与 DB 读回值保持一致
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime endTime = now.plusDays(5);
        LocalDateTime renewAt = now.minusDays(1);
        seedActiveSub(9001002L, endTime, renewAt);

        R<Integer> resp = memberClient.renewDueSubscriptions();
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isGreaterThanOrEqualTo(1);

        MemberSubscriptionEntity sub = subscriptionMapper.selectById(9001002L);
        assertThat(sub).isNotNull();
        // endTime 延长一个续费周期（30 天）
        assertThat(sub.getEndTime().isEqual(endTime.plusDays(30))).isTrue();
        // renewAt 前移（新 endTime − 窗口(10 天) = endTime + 20 天，推到未来）
        assertThat(sub.getRenewAt().isEqual(endTime.plusDays(20))).isTrue();
        // 续费成功标记清零
        assertThat(sub.getRenewLastStatus()).isEqualTo(0);
        assertThat(sub.getRenewFailCount()).isEqualTo(0);
        // 状态保持生效中
        assertThat(sub.getStatus()).isEqualTo(1);
    }

    @Test
    void renewal_failure_marksStatus_noExtend_noThrow() {
        // 覆盖 ② 失败路径：网关返回 failed（realSuccess=false）
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(MemberPaymentGateway.ChargeResult.failed("stub"));

        // H2(MySQL 兼容模式) 对 DATETIME 做纳秒截断，seed 值需 withNano(0) 与 DB 读回值保持一致
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime endTime = now.plusDays(5);
        LocalDateTime renewAt = now.minusDays(1);
        seedActiveSub(9001003L, endTime, renewAt);

        // 不抛异常，且续费成功条数为 0
        int count = memberService.renewDueSubscriptions();
        assertThat(count).isEqualTo(0);

        MemberSubscriptionEntity sub = subscriptionMapper.selectById(9001003L);
        assertThat(sub).isNotNull();
        // 保持生效中，endTime 不变，renewAt 不被前移（仍为过去）
        assertThat(sub.getStatus()).isEqualTo(1);
        assertThat(sub.getEndTime().isEqual(endTime)).isTrue();
        assertThat(sub.getRenewAt().isEqual(renewAt)).isTrue();
        // 标记续费失败、失败计数 +1
        assertThat(sub.getRenewLastStatus()).isEqualTo(1);
        assertThat(sub.getRenewFailCount()).isEqualTo(1);
    }
}

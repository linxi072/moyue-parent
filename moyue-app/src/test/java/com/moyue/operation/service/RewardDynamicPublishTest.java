package com.moyue.operation.service;

import com.moyue.api.content.client.BookClient;
import com.moyue.api.content.dto.BookSummaryDTO;
import com.moyue.api.social.client.DynamicClient;
import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.common.R;
import com.moyue.operation.entity.RewardOrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打赏动态旁路发布集成测试（P2-E · moyue-system）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；{@link DynamicClient} / {@link BookClient} 由 {@link MockBean} 替换。
 * 覆盖：RewardService.pay 成功经监听器调用 DynamicClient.publish（dynamicType=3，refId=orderId）；
 * DynamicClient 抛异常时打款主流程仍成功返回（旁路非阻断）。
 * profile 固定为 {@code test}（见 src/test/resources/application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class RewardDynamicPublishTest {

    @Autowired
    private RewardService rewardService;

    @MockBean
    private DynamicClient dynamicClient;

    @MockBean
    private BookClient bookClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM reward_order");
        jdbcTemplate.update("DELETE FROM author_income");
        jdbcTemplate.update("DELETE FROM user_dynamic");
        // pay 经 BookClient 取作者反规范化快照；mock 返回带 authorId 的书籍，避免真实 Feign 调用
        BookSummaryDTO book = new BookSummaryDTO();
        book.setBookId(1001L);
        book.setAuthorId(900020L);
        book.setAuthor("作者Z");
        book.setTitle("测试书Z");
        when(bookClient.getBook(anyLong())).thenReturn(R.ok(book));
    }

    @Test
    void pay_publishesType3DynamicWithOrderId() {
        RewardOrderEntity order = rewardService.createOrder(900050L, 1001L, null, new BigDecimal("10.00"), 1);
        RewardOrderEntity paid = rewardService.pay(order.getOrderNo(), 900050L);
        assertThat(paid.getStatus()).isEqualTo(1);

        ArgumentCaptor<DynamicPublishDTO> captor = ArgumentCaptor.forClass(DynamicPublishDTO.class);
        verify(dynamicClient).publish(captor.capture());
        DynamicPublishDTO dto = captor.getValue();
        assertThat(dto.getDynamicType()).isEqualTo(3);
        assertThat(dto.getRefType()).isEqualTo(2);
        assertThat(dto.getRefId()).isEqualTo(order.getId());
        assertThat(dto.getAuthorId()).isEqualTo(900020L);
    }

    @Test
    void pay_publishFailure_doesNotBlockPayment() {
        when(dynamicClient.publish(any())).thenThrow(new RuntimeException("social 不可用"));

        RewardOrderEntity order = rewardService.createOrder(900051L, 1001L, null, new BigDecimal("20.00"), 1);
        RewardOrderEntity paid = rewardService.pay(order.getOrderNo(), 900051L);
        // 打款主流程不受影响，订单仍置为已支付
        assertThat(paid.getStatus()).isEqualTo(1);
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reward_order WHERE id = ?", Integer.class, order.getId());
        assertThat(cnt).isEqualTo(1);
    }
}

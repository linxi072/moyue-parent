package com.moyue.author.service;

import com.moyue.api.system.client.IncomeClient;
import com.moyue.api.system.client.SettlementClient;
import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthorService 单测（Mockito 隔离 IncomeClient / SettlementClient，闭环 P2-K 测试补齐）：
 * 覆盖稿酬流水查询、结算单列表、单张结算单三类跨域读取，以及各自的降级分支
 * （client 为 null / 远端抛异常 / 返回非成功码 → 降级为空集合或 null，不阻断作者主页主流程）。
 */
class AuthorServiceTest {

    private final IncomeClient incomeClient = mock(IncomeClient.class);
    private final SettlementClient settlementClient = mock(SettlementClient.class);

    private AuthorService service;

    @BeforeEach
    void setUp() {
        service = new AuthorService();
        ReflectionTestUtils.setField(service, "incomeClient", incomeClient);
        ReflectionTestUtils.setField(service, "settlementClient", settlementClient);
    }

    private AuthorIncomeDTO income(Long id, String month) {
        AuthorIncomeDTO d = new AuthorIncomeDTO();
        d.setId(id);
        d.setSettleMonth(month);
        return d;
    }

    private SettlementDTO settlement(Long id) {
        SettlementDTO d = new SettlementDTO();
        d.setId(id);
        return d;
    }

    // ---- listByAuthor ----

    @Test
    @DisplayName("listByAuthor：client 为 null 降级为空集合")
    void listByAuthor_clientNull_degradesEmpty() {
        ReflectionTestUtils.setField(service, "incomeClient", null);
        assertThat(service.listByAuthor(1L)).isEmpty();
    }

    @Test
    @DisplayName("listByAuthor：成功返回 data")
    void listByAuthor_success_returnsData() {
        List<AuthorIncomeDTO> data = List.of(income(1L, "2026-09"), income(2L, "2026-08"));
        when(incomeClient.listByAuthor(1L)).thenReturn(R.ok(data));
        assertThat(service.listByAuthor(1L)).containsExactlyElementsOf(data);
    }

    @Test
    @DisplayName("listByAuthor：client 抛异常降级为空集合")
    void listByAuthor_exception_degradesEmpty() {
        when(incomeClient.listByAuthor(1L)).thenThrow(new RuntimeException("feign down"));
        assertThat(service.listByAuthor(1L)).isEmpty();
    }

    @Test
    @DisplayName("listByAuthor：返回非成功码降级为空集合")
    void listByAuthor_degradedCode_empty() {
        when(incomeClient.listByAuthor(1L)).thenReturn(R.fail(ResultCode.SERVICE_DEGRADED));
        assertThat(service.listByAuthor(1L)).isEmpty();
    }

    // ---- listSettlements ----

    @Test
    @DisplayName("listSettlements：client 为 null 降级为空集合")
    void listSettlements_clientNull_degradesEmpty() {
        ReflectionTestUtils.setField(service, "settlementClient", null);
        assertThat(service.listSettlements(1L)).isEmpty();
    }

    @Test
    @DisplayName("listSettlements：成功返回 data")
    void listSettlements_success_returnsData() {
        List<SettlementDTO> data = List.of(settlement(10L), settlement(11L));
        when(settlementClient.listSettlements(1L)).thenReturn(R.ok(data));
        assertThat(service.listSettlements(1L)).containsExactlyElementsOf(data);
    }

    @Test
    @DisplayName("listSettlements：client 抛异常降级为空集合")
    void listSettlements_exception_degradesEmpty() {
        when(settlementClient.listSettlements(1L)).thenThrow(new RuntimeException("down"));
        assertThat(service.listSettlements(1L)).isEmpty();
    }

    // ---- getSettlement ----

    @Test
    @DisplayName("getSettlement：client 为 null 降级为 null")
    void getSettlement_clientNull_degradesNull() {
        ReflectionTestUtils.setField(service, "settlementClient", null);
        assertThat(service.getSettlement(10L)).isNull();
    }

    @Test
    @DisplayName("getSettlement：成功返回 data")
    void getSettlement_success_returnsData() {
        SettlementDTO d = settlement(10L);
        when(settlementClient.getSettlement(10L)).thenReturn(R.ok(d));
        assertThat(service.getSettlement(10L)).isSameAs(d);
    }

    @Test
    @DisplayName("getSettlement：client 抛异常降级为 null")
    void getSettlement_exception_degradesNull() {
        when(settlementClient.getSettlement(10L)).thenThrow(new RuntimeException("down"));
        assertThat(service.getSettlement(10L)).isNull();
    }
}

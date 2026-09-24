package com.moyue.operation.service;

import com.moyue.api.system.dto.AuthorIncomeDTO;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthorIncomeService 单测（Mockito 隔离 Mapper，闭环 P2-H 解耦收尾）：
 * 验证「按作者查询委托 Mapper + 实体→契约 DTO 映射」「空 authorId 返回空集合」「不触碰写链路」。
 * 写法与 {@code SettlementService} 等 domain 服务单测对齐（ReflectionTestUtils 注入 mock，不启 Spring）。
 */
class AuthorIncomeServiceTest {

    private final AuthorIncomeMapper mapper = mock(AuthorIncomeMapper.class);

    private AuthorIncomeService createService() {
        AuthorIncomeService s = new AuthorIncomeService();
        ReflectionTestUtils.setField(s, "authorIncomeMapper", mapper);
        return s;
    }

    private AuthorIncomeEntity income(Long id, Long authorId, String month, BigDecimal amount) {
        AuthorIncomeEntity e = new AuthorIncomeEntity();
        e.setId(id);
        e.setAuthorId(authorId);
        e.setBookId(99L);
        e.setOrderNo("RW" + id);
        e.setIncomeType(2);
        e.setAmount(amount);
        e.setSettleMonth(month);
        return e;
    }

    @Test
    @DisplayName("listByAuthor 委托 Mapper 并按契约映射，按结算月份倒序返回 DTO")
    void listByAuthor_mapsToContractDto() {
        AuthorIncomeService service = createService();
        when(mapper.selectList(any())).thenReturn(List.of(
                income(1L, 7001L, "2026-09", new BigDecimal("140.00")),
                income(2L, 7001L, "2026-08", new BigDecimal("70.00"))));

        List<AuthorIncomeDTO> dtos = service.listByAuthor(7001L);

        assertThat(dtos).hasSize(2);
        // 倒序：2026-09 在前
        assertThat(dtos.get(0).getSettleMonth()).isEqualTo("2026-09");
        assertThat(dtos.get(0).getAmount()).isEqualByComparingTo("140.00");
        assertThat(dtos.get(0).getAuthorId()).isEqualTo(7001L);
        assertThat(dtos.get(1).getSettleMonth()).isEqualTo("2026-08");
        // 字段映射完整性
        assertThat(dtos.get(0).getIncomeType()).isEqualTo(2);
        assertThat(dtos.get(0).getOrderNo()).isEqualTo("RW1");
    }

    @Test
    @DisplayName("listByAuthor authorId 为空：返回空集合，不抛异常")
    void listByAuthor_nullAuthorId_returnsEmpty() {
        AuthorIncomeService service = createService();

        assertThat(service.listByAuthor(null)).isEmpty();
    }

    @Test
    @DisplayName("listByAuthor 作者无任何流水：返回空集合")
    void listByAuthor_noRows_returnsEmpty() {
        AuthorIncomeService service = createService();
        when(mapper.selectList(any())).thenReturn(List.of());

        assertThat(service.listByAuthor(7001L)).isEmpty();
    }
}

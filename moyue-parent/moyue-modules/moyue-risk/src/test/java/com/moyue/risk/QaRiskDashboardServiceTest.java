package com.moyue.risk;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.risk.report.ReportEntity;
import com.moyue.risk.report.ReportMapper;
import com.moyue.risk.sensitive.SensitiveWordEntity;
import com.moyue.risk.sensitive.SensitiveWordMapper;
import com.moyue.risk.sensitive.SensitiveWordService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RiskDashboardService 聚合统计纯 Mockito 单测（不启 Spring / 不依赖 Docker）。
 * 验证敏感词命中累计、审核任务按 status 计数、举报按 status 计数（仅未删除）与 TOP 命中透传。
 */
class QaRiskDashboardServiceTest {

    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditTaskEntity.class);
        TableInfoHelper.initTableInfo(assistant, ReportEntity.class);
        TableInfoHelper.initTableInfo(assistant, SensitiveWordEntity.class);
    }

    private final AuditTaskMapper auditTaskMapper = mock(AuditTaskMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
    private final SensitiveWordMapper sensitiveWordMapper = mock(SensitiveWordMapper.class);

    private RiskDashboardService service() {
        RiskDashboardService s = new RiskDashboardService();
        ReflectionTestUtils.setField(s, "auditTaskMapper", auditTaskMapper);
        ReflectionTestUtils.setField(s, "reportMapper", reportMapper);
        ReflectionTestUtils.setField(s, "sensitiveWordService", sensitiveWordService);
        ReflectionTestUtils.setField(s, "sensitiveWordMapper", sensitiveWordMapper);
        return s;
    }

    private SensitiveWordEntity word(long id, String w, int level, int hit) {
        SensitiveWordEntity e = new SensitiveWordEntity();
        e.setId(id);
        e.setWord(w);
        e.setLevel(level);
        e.setHitCount(hit);
        return e;
    }

    @Test
    @DisplayName("看板聚合：敏感词命中累计 + 审核/举报按 status 计数 + TOP 命中透传")
    void getDashboard_aggregatesAllMetrics() {
        when(sensitiveWordMapper.selectCount(any())).thenReturn(12L);
        when(sensitiveWordMapper.sumHitCount()).thenReturn(123L);
        when(sensitiveWordService.hitStats(10))
                .thenReturn(List.of(word(1L, "示例敏感词A", 1, 50), word(2L, "示例灰词C", 2, 30)));
        // 审核任务 status 0/1/2/3 → 5/2/0/1
        when(auditTaskMapper.selectCount(any())).thenReturn(5L, 2L, 0L, 1L);
        // 举报 status 0/1/2（仅未删除）→ 3/1/0
        when(reportMapper.selectCount(any())).thenReturn(3L, 1L, 0L);

        RiskDashboardVO vo = service().getDashboard();

        assertThat(vo.getSensitiveWordTotal()).isEqualTo(12L);
        assertThat(vo.getSensitiveWordHitTotal()).isEqualTo(123L);
        assertThat(vo.getTopHitWords()).hasSize(2);
        assertThat(vo.getTopHitWords().get(0).getWord()).isEqualTo("示例敏感词A");

        assertThat(vo.getAuditTaskByStatus()).containsEntry(0, 5L)
                .containsEntry(1, 2L).containsEntry(2, 0L).containsEntry(3, 1L);
        assertThat(vo.getAuditTaskTotal()).isEqualTo(8L);

        assertThat(vo.getReportByStatus()).containsEntry(0, 3L).containsEntry(1, 1L).containsEntry(2, 0L);
        assertThat(vo.getReportTotal()).isEqualTo(4L);
    }

    @Test
    @DisplayName("看板空数据：命中累计为 null 时回退 0，各计数归零不抛异常")
    void getDashboard_nullHitCount_fallsBackToZero() {
        when(sensitiveWordMapper.selectCount(any())).thenReturn(0L);
        when(sensitiveWordMapper.sumHitCount()).thenReturn(null);
        when(sensitiveWordService.hitStats(10)).thenReturn(List.of());
        when(auditTaskMapper.selectCount(any())).thenReturn(0L, 0L, 0L, 0L);
        when(reportMapper.selectCount(any())).thenReturn(0L, 0L, 0L);

        RiskDashboardVO vo = service().getDashboard();

        assertThat(vo.getSensitiveWordHitTotal()).isZero();
        assertThat(vo.getAuditTaskTotal()).isZero();
        assertThat(vo.getReportTotal()).isZero();
    }
}

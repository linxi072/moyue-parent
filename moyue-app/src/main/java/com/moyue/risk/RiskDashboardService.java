package com.moyue.risk;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.risk.report.ReportEntity;
import com.moyue.risk.report.ReportMapper;
import com.moyue.risk.sensitive.SensitiveWordEntity;
import com.moyue.risk.sensitive.SensitiveWordMapper;
import com.moyue.risk.sensitive.SensitiveWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 内容安全统计看板（P1-4）：聚合敏感词命中、审核任务与举报三类指标。
 *
 * <p>纯只读聚合，复用 sensitive_word / audit_task / report 三张表，
 * 不引入新表（与实现计划-P0P1.md §P1-4 一致）。status 维度按各表枚举范围计数，
 * audit_task 无 is_deleted 列（实体注释明示），report 按业务约定显式过滤 is_deleted=0。</p>
 */
@Service
public class RiskDashboardService {

    /** 审核任务状态枚举上界：0 待投递 / 1 已投递 / 2 已完成 / 3 死信 */
    private static final int AUDIT_STATUS_MAX = 3;
    /** 举报状态枚举上界：0 待处理 / 1 属实 / 2 驳回 */
    private static final int REPORT_STATUS_MAX = 2;

    @Autowired
    private AuditTaskMapper auditTaskMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    /** 聚合看板数据 */
    public RiskDashboardVO getDashboard() {
        RiskDashboardVO vo = new RiskDashboardVO();

        // 敏感词：总数 + 命中累计 + 命中 TOP10
        long wordTotal = sensitiveWordMapper.selectCount(Wrappers.<SensitiveWordEntity>lambdaQuery()
                .eq(SensitiveWordEntity::getIsDeleted, 0));
        vo.setSensitiveWordTotal(wordTotal);
        Long hitTotal = sensitiveWordMapper.sumHitCount();
        vo.setSensitiveWordHitTotal(hitTotal == null ? 0L : hitTotal);
        vo.setTopHitWords(sensitiveWordService.hitStats(10).stream().map(e -> {
            RiskDashboardVO.TopHitWord w = new RiskDashboardVO.TopHitWord();
            w.setId(e.getId());
            w.setWord(e.getWord());
            w.setLevel(e.getLevel());
            w.setHitCount(e.getHitCount());
            return w;
        }).toList());

        // 审核任务按 status 计数（无 is_deleted 列）
        Map<Integer, Long> auditByStatus = new HashMap<>();
        long auditTotal = 0L;
        for (int s = 0; s <= AUDIT_STATUS_MAX; s++) {
            long c = auditTaskMapper.selectCount(Wrappers.<AuditTaskEntity>lambdaQuery()
                    .eq(AuditTaskEntity::getStatus, s));
            auditByStatus.put(s, c);
            auditTotal += c;
        }
        vo.setAuditTaskByStatus(auditByStatus);
        vo.setAuditTaskTotal(auditTotal);

        // 举报按 status 计数（仅未删除）
        Map<Integer, Long> reportByStatus = new HashMap<>();
        long reportTotal = 0L;
        for (int s = 0; s <= REPORT_STATUS_MAX; s++) {
            long c = reportMapper.selectCount(Wrappers.<ReportEntity>lambdaQuery()
                    .eq(ReportEntity::getIsDeleted, 0)
                    .eq(ReportEntity::getStatus, s));
            reportByStatus.put(s, c);
            reportTotal += c;
        }
        vo.setReportByStatus(reportByStatus);
        vo.setReportTotal(reportTotal);

        return vo;
    }
}

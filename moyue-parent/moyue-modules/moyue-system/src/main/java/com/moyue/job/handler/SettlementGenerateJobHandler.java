package com.moyue.job.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moyue.operation.entity.AuthorIncomeEntity;
import com.moyue.operation.mapper.AuthorIncomeMapper;
import com.moyue.operation.service.SettlementService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 月度结算单预生成任务（P0-1 双触发之一）。
 * 每月为「当月有未结算稿酬流水(incomeType IN (2,4) 且 settlement_id IS NULL)」的作者生成结算单。
 * 复用 platform 唯一 XXL-Job 执行器（appname=moyue-job）；失败仅记日志不抛。
 */
@Component
public class SettlementGenerateJobHandler {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private AuthorIncomeMapper authorIncomeMapper;

    @XxlJob("settlementGenerateJob")
    public void settlementGenerateJob() {
        String period = LocalDate.now().format(MONTH_FMT);
        List<Long> authorIds = findUnsettledAuthors(period);
        int generated = 0;
        for (Long authorId : authorIds) {
            try {
                settlementService.createSettlement(authorId, period);
                generated++;
            } catch (Exception ex) {
                // 单作者失败不影响其余作者，仅记日志
                XxlJobHelper.log("生成结算单失败 authorId={} period={}：{}", authorId, period, ex.getMessage());
            }
        }
        XxlJobHelper.log("settlementGenerateJob period={} 待结算作者={} 成功生成={}", period, authorIds.size(), generated);
        XxlJobHelper.handleSuccess("settlementGenerateJob done generated=" + generated);
    }

    /** 查当月有未结算稿酬的去重 author_id 列表 */
    private List<Long> findUnsettledAuthors(String period) {
        QueryWrapper<AuthorIncomeEntity> qw = new QueryWrapper<>();
        qw.select("DISTINCT author_id");
        qw.eq("settle_month", period);
        qw.isNull("settlement_id");
        qw.in("income_type", 2, 4);
        List<Object> objs = authorIncomeMapper.selectObjs(qw);
        return objs.stream()
                .filter(Objects::nonNull)
                .map(o -> Long.valueOf(o.toString()))
                .collect(Collectors.toList());
    }
}

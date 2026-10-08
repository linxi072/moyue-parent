package com.moyue.risk.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.risk.domain.dto.query.ReportTicketQuery;
import com.moyue.risk.domain.entity.ReportTicket;
import com.moyue.risk.domain.vo.ReportTicketVO;

/**
 * 举报工单管理（风控域）：提交 / 列表 / 处理（状态流转）。
 *
 * @author moyue
 */
public interface ReportService {

    PageResult<ReportTicketVO> pageReports(ReportTicketQuery query);

    Long submit(ReportTicket entity);

    /**
     * 处理举报工单：status 1 已处理 / 2 驳回；记录处理人与处理说明。
     */
    boolean handle(Long id, Integer status, String handler, String handleReason);
}

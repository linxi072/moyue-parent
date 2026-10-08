package com.moyue.risk.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.risk.domain.dto.query.ReportTicketQuery;
import com.moyue.risk.domain.entity.ReportTicket;
import com.moyue.risk.domain.vo.ReportTicketVO;
import com.moyue.risk.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报工单管理（风控域）：提交 / 列表 / 处理（状态流转）。
 *
 * @author moyue
 */
@Tag(name = "举报工单", description = "作品 / 评论 / 用户 / 帖子举报的提交、查看与处理")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/risk/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "工单分页")
    @RequiresPermissions("risk:report:list")
    @GetMapping
    public R<PageResult<ReportTicketVO>> page(ReportTicketQuery query) {
        return R.ok(reportService.pageReports(query));
    }

    @Operation(summary = "提交举报")
    @RequiresPermissions("risk:report:add")
    @Log(title = "举报工单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> submit(@RequestBody ReportTicket entity) {
        return R.ok(reportService.submit(entity));
    }

    @Operation(summary = "处理工单", description = "status 1 已处理 / 2 驳回；待处理工单才能处理")
    @RequiresPermissions("risk:report:handle")
    @Log(title = "举报工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/handle")
    public R<Boolean> handle(@PathVariable Long id,
                             @RequestParam Integer status,
                             @RequestParam(required = false) String handler,
                             @RequestParam(required = false) String handleReason) {
        return R.ok(reportService.handle(id, status, handler, handleReason));
    }
}

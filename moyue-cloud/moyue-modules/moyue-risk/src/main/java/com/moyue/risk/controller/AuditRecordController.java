package com.moyue.risk.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.risk.domain.dto.query.AuditRecordQuery;
import com.moyue.risk.domain.entity.AuditRecord;
import com.moyue.risk.domain.vo.AuditRecordVO;
import com.moyue.risk.service.AuditRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核工单管理（风控域）：CRUD + 审核通过 / 驳回。
 *
 * @author moyue
 */
@Tag(name = "审核工单", description = "作品 / 评论 / 封面审核，支持通过与驳回")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/risk/audit-records")
@RequiredArgsConstructor
public class AuditRecordController {

    private final AuditRecordService auditService;

    @Operation(summary = "工单分页")
    @RequiresPermissions("risk:audit:list")
    @GetMapping
    public R<PageResult<AuditRecordVO>> page(AuditRecordQuery query) {
        return R.ok(auditService.pageAudits(query));
    }

    @Operation(summary = "新建工单")
    @RequiresPermissions("risk:audit:add")
    @Log(title = "审核工单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody AuditRecord entity) {
        return R.ok(auditService.createAudit(entity));
    }

    @Operation(summary = "编辑工单")
    @RequiresPermissions("risk:audit:edit")
    @Log(title = "审核工单", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody AuditRecord entity) {
        entity.setId(id);
        return R.ok(auditService.updateAudit(entity));
    }

    @Operation(summary = "删除工单")
    @RequiresPermissions("risk:audit:remove")
    @Log(title = "审核工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(auditService.deleteAudit(id));
    }

    @Operation(summary = "审核通过")
    @RequiresPermissions("risk:audit:audit")
    @Log(title = "审核工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/approve")
    public R<Boolean> approve(@PathVariable Long id, @RequestParam(required = false) String auditor) {
        return R.ok(auditService.approve(id, auditor));
    }

    @Operation(summary = "审核驳回")
    @RequiresPermissions("risk:audit:audit")
    @Log(title = "审核工单", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/reject")
    public R<Boolean> reject(@PathVariable Long id,
                             @RequestParam(required = false) String auditor,
                             @RequestParam(required = false) String reason) {
        return R.ok(auditService.reject(id, auditor, reason));
    }
}

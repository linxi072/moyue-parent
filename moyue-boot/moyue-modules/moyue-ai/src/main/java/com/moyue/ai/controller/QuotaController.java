package com.moyue.ai.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.ai.domain.dto.query.AiQuotaQuery;
import com.moyue.ai.domain.vo.AiQuotaVO;
import com.moyue.ai.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 配额管理（运营端）：列表 / 重置。
 *
 * @author moyue
 */
@Tag(name = "AI 配额", description = "用户 token 配额查询与重置")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/ai/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @Operation(summary = "配额分页")
    @RequiresPermissions("ai:quota:list")
    @GetMapping
    public R<PageResult<AiQuotaVO>> page(AiQuotaQuery query) {
        return R.ok(quotaService.pageQuotas(query));
    }

    @Operation(summary = "重置配额", description = "used=0, remain=total")
    @RequiresPermissions("ai:quota:reset")
    @Log(title = "AI 配额", businessType = BusinessType.UPDATE)
    @PostMapping("/reset")
    public R<Boolean> reset(@RequestParam Long userId, @RequestParam(defaultValue = "1000") Integer total) {
        quotaService.reset(userId, total);
        return R.ok(true);
    }
}

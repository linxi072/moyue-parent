package com.moyue.risk.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.risk.domain.dto.query.SensitiveWordQuery;
import com.moyue.risk.domain.entity.SensitiveWord;
import com.moyue.risk.domain.vo.SensitiveWordVO;
import com.moyue.risk.service.SensitiveService;
import com.moyue.risk.service.SensitiveWordService;
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
 * 敏感词管理（风控域）：CRUD + 启用 / 停用 + 文本检测查询。
 *
 * @author moyue
 */
@Tag(name = "敏感词", description = "敏感词分级（拦截 / 告警）维护，并提供文本检测查询接口")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/risk/sensitive-words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;
    private final SensitiveService sensitiveService;

    @Operation(summary = "敏感词分页")
    @RequiresPermissions("risk:sensitive:list")
    @GetMapping
    public R<PageResult<SensitiveWordVO>> page(SensitiveWordQuery query) {
        return R.ok(sensitiveWordService.pageWords(query));
    }

    @Operation(summary = "新建敏感词")
    @RequiresPermissions("risk:sensitive:add")
    @Log(title = "敏感词", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SensitiveWord entity) {
        return R.ok(sensitiveWordService.createWord(entity));
    }

    @Operation(summary = "编辑敏感词")
    @RequiresPermissions("risk:sensitive:edit")
    @Log(title = "敏感词", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SensitiveWord entity) {
        entity.setId(id);
        return R.ok(sensitiveWordService.updateWord(entity));
    }

    @Operation(summary = "删除敏感词")
    @RequiresPermissions("risk:sensitive:remove")
    @Log(title = "敏感词", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(sensitiveWordService.deleteWord(id));
    }

    @Operation(summary = "启用")
    @RequiresPermissions("risk:sensitive:edit")
    @Log(title = "敏感词", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/enable")
    public R<Boolean> enable(@PathVariable Long id) {
        return R.ok(sensitiveWordService.enable(id));
    }

    @Operation(summary = "停用")
    @RequiresPermissions("risk:sensitive:edit")
    @Log(title = "敏感词", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/disable")
    public R<Boolean> disable(@PathVariable Long id) {
        return R.ok(sensitiveWordService.disable(id));
    }

    @Operation(summary = "文本是否命中敏感词", description = "供 content / social 等模块校验；命中累加计数")
    @GetMapping("/contains")
    public R<Boolean> contains(@RequestParam String text) {
        return R.ok(sensitiveService.contains(text));
    }

    @Operation(summary = "命中敏感词级别", description = "1 拦截 / 2 告警；未命中返回 null")
    @GetMapping("/match-level")
    public R<Integer> matchLevel(@RequestParam String text) {
        return R.ok(sensitiveService.matchLevel(text).orElse(null));
    }
}

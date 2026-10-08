package com.moyue.ai.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.ai.domain.dto.query.AiTaskQuery;
import com.moyue.ai.domain.entity.AiTask;
import com.moyue.ai.domain.vo.AiTaskVO;
import com.moyue.ai.service.AiTaskService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 任务管理（AI 域）：CRUD + 任务概览。
 *
 * @author moyue
 */
@Tag(name = "AI 任务", description = "续写 / 润色 / 摘要 / 大纲任务增删改查与概览")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/ai/tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService taskService;

    @Operation(summary = "任务分页")
    @RequiresPermissions("ai:task:list")
    @GetMapping
    public R<PageResult<AiTaskVO>> page(AiTaskQuery query) {
        return R.ok(taskService.pageTasks(query));
    }

    @Operation(summary = "新建任务")
    @RequiresPermissions("ai:task:add")
    @Log(title = "AI 任务", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody AiTask entity) {
        return R.ok(taskService.createTask(entity));
    }

    @Operation(summary = "编辑任务")
    @RequiresPermissions("ai:task:edit")
    @Log(title = "AI 任务", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody AiTask entity) {
        entity.setId(id);
        return R.ok(taskService.updateTask(entity));
    }

    @Operation(summary = "删除任务")
    @RequiresPermissions("ai:task:remove")
    @Log(title = "AI 任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(taskService.deleteTask(id));
    }

    @Operation(summary = "任务概览", description = "总数 / 待处理 / 成功 / 失败 / 总 token")
    @RequiresPermissions("ai:task:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(taskService.summary());
    }

    @Operation(summary = "运行任务", description = "状态 0→1（写结果 + 扣配额）；配额不足拒绝并保持待处理")
    @RequiresPermissions("ai:task:run")
    @Log(title = "AI 任务", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/run")
    public R<Boolean> run(@PathVariable Long id) {
        return R.ok(taskService.run(id));
    }
}

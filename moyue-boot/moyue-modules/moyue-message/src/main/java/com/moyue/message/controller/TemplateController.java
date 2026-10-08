package com.moyue.message.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.message.domain.dto.query.MessageTemplateQuery;
import com.moyue.message.domain.entity.MessageTemplate;
import com.moyue.message.domain.vo.MessageTemplateVO;
import com.moyue.message.service.TemplateService;
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

/**
 * 消息模板管理（消息域）：CRUD + 启用 / 停用。
 *
 * @author moyue
 */
@Tag(name = "消息模板", description = "群发站内信引用的消息模板增删改查与启用停用")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/message/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "模板分页")
    @RequiresPermissions("message:template:list")
    @GetMapping
    public R<PageResult<MessageTemplateVO>> page(MessageTemplateQuery query) {
        return R.ok(templateService.pageTemplates(query));
    }

    @Operation(summary = "新建模板")
    @RequiresPermissions("message:template:add")
    @Log(title = "消息模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody MessageTemplate entity) {
        return R.ok(templateService.createTemplate(entity));
    }

    @Operation(summary = "编辑模板")
    @RequiresPermissions("message:template:edit")
    @Log(title = "消息模板", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody MessageTemplate entity) {
        entity.setId(id);
        return R.ok(templateService.updateTemplate(entity));
    }

    @Operation(summary = "删除模板")
    @RequiresPermissions("message:template:remove")
    @Log(title = "消息模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(templateService.deleteTemplate(id));
    }

    @Operation(summary = "启用")
    @RequiresPermissions("message:template:edit")
    @Log(title = "消息模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/enable")
    public R<Boolean> enable(@PathVariable Long id) {
        return R.ok(templateService.enable(id));
    }

    @Operation(summary = "停用")
    @RequiresPermissions("message:template:edit")
    @Log(title = "消息模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/disable")
    public R<Boolean> disable(@PathVariable Long id) {
        return R.ok(templateService.disable(id));
    }
}

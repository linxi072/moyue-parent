package com.moyue.system.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.system.domain.dto.query.ConfigQuery;
import com.moyue.system.domain.entity.SysConfig;
import com.moyue.system.domain.vo.ConfigVO;
import com.moyue.system.service.SysConfigService;
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
 * 参数管理（⑥）：5 个端点。
 *
 * @author moyue
 */
@Tag(name = "参数管理", description = "系统参数增删改查，走 Redis 缓存")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/system/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    @Operation(summary = "参数分页")
    @RequiresPermissions("system:config:list")
    @GetMapping
    public R<PageResult<ConfigVO>> page(ConfigQuery query) {
        return R.ok(configService.pageConfigs(query));
    }

    @Operation(summary = "新建参数", description = "config_key 唯一")
    @RequiresPermissions("system:config:add")
    @Log(title = "参数管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SysConfig entity) {
        return R.ok(configService.createConfig(entity));
    }

    @Operation(summary = "编辑参数")
    @RequiresPermissions("system:config:edit")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{configId}")
    public R<Boolean> update(@PathVariable Long configId, @RequestBody SysConfig entity) {
        entity.setId(configId);
        return R.ok(configService.updateConfig(entity));
    }

    @Operation(summary = "删除参数", description = "config_type = 1 系统内置不可删")
    @RequiresPermissions("system:config:remove")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configId}")
    public R<Boolean> delete(@PathVariable Long configId) {
        return R.ok(configService.deleteConfig(configId));
    }

    @Operation(summary = "按键名取参数值", description = "走缓存")
    @GetMapping("/key/{configKey}")
    public R<String> byKey(@PathVariable String configKey) {
        return R.ok(configService.getValueByKey(configKey));
    }
}

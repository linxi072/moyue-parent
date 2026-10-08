package com.moyue.search.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.search.domain.dto.query.BlockWordQuery;
import com.moyue.search.domain.entity.BlockWord;
import com.moyue.search.domain.vo.BlockWordVO;
import com.moyue.search.service.BlockWordService;
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
 * 搜索屏蔽词管理（搜索域）：CRUD + 启用 / 停用。
 *
 * @author moyue
 */
@Tag(name = "屏蔽词", description = "搜索屏蔽词增删改查与启用停用")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/search/block-words")
@RequiredArgsConstructor
public class BlockWordController {

    private final BlockWordService blockWordService;

    @Operation(summary = "屏蔽词分页")
    @RequiresPermissions("search:blockword:list")
    @GetMapping
    public R<PageResult<BlockWordVO>> page(BlockWordQuery query) {
        return R.ok(blockWordService.pageBlockWords(query));
    }

    @Operation(summary = "新建屏蔽词")
    @RequiresPermissions("search:blockword:add")
    @Log(title = "屏蔽词", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody BlockWord entity) {
        return R.ok(blockWordService.createBlockWord(entity));
    }

    @Operation(summary = "编辑屏蔽词")
    @RequiresPermissions("search:blockword:edit")
    @Log(title = "屏蔽词", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody BlockWord entity) {
        entity.setId(id);
        return R.ok(blockWordService.updateBlockWord(entity));
    }

    @Operation(summary = "删除屏蔽词")
    @RequiresPermissions("search:blockword:remove")
    @Log(title = "屏蔽词", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(blockWordService.deleteBlockWord(id));
    }

    @Operation(summary = "启用")
    @RequiresPermissions("search:blockword:edit")
    @Log(title = "屏蔽词", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/enable")
    public R<Boolean> enable(@PathVariable Long id) {
        return R.ok(blockWordService.enable(id));
    }

    @Operation(summary = "停用")
    @RequiresPermissions("search:blockword:edit")
    @Log(title = "屏蔽词", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/disable")
    public R<Boolean> disable(@PathVariable Long id) {
        return R.ok(blockWordService.disable(id));
    }
}

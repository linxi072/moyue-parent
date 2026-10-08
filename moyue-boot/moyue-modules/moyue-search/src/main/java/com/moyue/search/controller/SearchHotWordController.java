package com.moyue.search.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.search.domain.dto.query.SearchHotWordQuery;
import com.moyue.search.domain.entity.SearchHotWord;
import com.moyue.search.domain.vo.SearchHotWordVO;
import com.moyue.search.service.SearchHotWordService;
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

import java.util.List;

/**
 * 搜索热词管理（搜索域）：CRUD + 热词榜。
 *
 * @author moyue
 */
@Tag(name = "搜索热词", description = "搜索热词增删改查与热词榜")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/search/hot-words")
@RequiredArgsConstructor
public class SearchHotWordController {

    private final SearchHotWordService hotWordService;

    @Operation(summary = "热词分页")
    @RequiresPermissions("search:hotword:list")
    @GetMapping
    public R<PageResult<SearchHotWordVO>> page(SearchHotWordQuery query) {
        return R.ok(hotWordService.pageHotWords(query));
    }

    @Operation(summary = "新建热词")
    @RequiresPermissions("search:hotword:add")
    @Log(title = "搜索热词", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody SearchHotWord entity) {
        return R.ok(hotWordService.createHotWord(entity));
    }

    @Operation(summary = "编辑热词")
    @RequiresPermissions("search:hotword:edit")
    @Log(title = "搜索热词", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SearchHotWord entity) {
        entity.setId(id);
        return R.ok(hotWordService.updateHotWord(entity));
    }

    @Operation(summary = "删除热词")
    @RequiresPermissions("search:hotword:remove")
    @Log(title = "搜索热词", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(hotWordService.deleteHotWord(id));
    }

    @Operation(summary = "热词榜", description = "启用中的热词按权重倒序取前 N")
    @RequiresPermissions("search:hotword:list")
    @GetMapping("/top")
    public R<List<SearchHotWordVO>> top(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(hotWordService.top(limit));
    }

    @Operation(summary = "搜索联想", description = "前缀匹配热词（MySQL 降级，不接 ES）")
    @RequiresPermissions("search:hotword:list")
    @GetMapping("/suggest")
    public R<List<SearchHotWordVO>> suggest(@RequestParam String keyword) {
        return R.ok(hotWordService.suggest(keyword));
    }
}

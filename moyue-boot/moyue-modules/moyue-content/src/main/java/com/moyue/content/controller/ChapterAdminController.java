package com.moyue.content.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.content.domain.dto.query.ChapterQuery;
import com.moyue.content.domain.vo.ChapterVO;
import com.moyue.content.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章节管理（内容域·运营端）：列表 / 发布 / 下架 / 排序 / 删除。
 *
 * @author moyue
 */
@Tag(name = "章节管理", description = "章节发布 / 下架 / 排序 / 删除")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/content/chapters")
@RequiredArgsConstructor
public class ChapterAdminController {

    private final ChapterService chapterService;

    @Operation(summary = "章节分页")
    @RequiresPermissions("content:chapter:list")
    @GetMapping
    public R<PageResult<ChapterVO>> page(ChapterQuery query) {
        return R.ok(chapterService.pageChapters(query));
    }

    @Operation(summary = "发布章节", description = "status → 1（立即发布）")
    @RequiresPermissions("content:chapter:publish")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/publish")
    public R<ChapterVO> publish(@PathVariable Long id) {
        return R.ok(chapterService.publish(id, null));
    }

    @Operation(summary = "下架章节", description = "status → 0（回到草稿）")
    @RequiresPermissions("content:chapter:publish")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/offshelf")
    public R<Boolean> offshelf(@PathVariable Long id) {
        return R.ok(chapterService.offshelf(id));
    }

    @Operation(summary = "调整章节序号", description = "与目标序号章节互换，保持目录连续")
    @RequiresPermissions("content:chapter:edit")
    @Log(title = "章节管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/reorder")
    public R<Boolean> reorder(@PathVariable Long id, @RequestParam int targetNo) {
        return R.ok(chapterService.reorder(id, targetNo));
    }

    @Operation(summary = "删除章节")
    @RequiresPermissions("content:chapter:remove")
    @Log(title = "章节管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(chapterService.deleteChapter(id));
    }
}

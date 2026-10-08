package com.moyue.content.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.content.domain.dto.query.BookShelfQuery;
import com.moyue.content.domain.vo.BookShelfVO;
import com.moyue.content.service.BookShelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户书架管理（内容域·运营端，只读）。
 *
 * @author moyue
 */
@Tag(name = "书架管理", description = "用户收藏书架查询（只读）")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/content/bookshelf")
@RequiredArgsConstructor
public class BookShelfAdminController {

    private final BookShelfService shelfService;

    @Operation(summary = "书架分页（全局，可选作品名模糊）")
    @RequiresPermissions("content:bookshelf:list")
    @GetMapping
    public R<PageResult<BookShelfVO>> page(BookShelfQuery query) {
        return R.ok(shelfService.adminPage(query));
    }
}

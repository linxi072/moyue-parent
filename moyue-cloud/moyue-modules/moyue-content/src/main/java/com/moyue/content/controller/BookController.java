package com.moyue.content.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.core.result.R;
import com.moyue.common.log.annotation.Log;
import com.moyue.common.log.enums.BusinessType;
import com.moyue.common.security.annotation.RequiresPermissions;
import com.moyue.content.domain.dto.query.BookQuery;
import com.moyue.content.domain.entity.Book;
import com.moyue.content.domain.vo.BookVO;
import com.moyue.content.service.BookService;
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
 * 作品管理（内容域）：CRUD + 热门榜。
 *
 * @author moyue
 */
@Tag(name = "作品管理", description = "小说作品增删改查与热门榜")
@Validated
@RestController
@RequestMapping(Constants.ADMIN_PATH_PREFIX + "/content/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @Operation(summary = "作品分页")
    @RequiresPermissions("content:book:list")
    @GetMapping
    public R<PageResult<BookVO>> page(BookQuery query) {
        return R.ok(bookService.pageBooks(query));
    }

    @Operation(summary = "新建作品")
    @RequiresPermissions("content:book:add")
    @Log(title = "作品管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> create(@RequestBody Book entity) {
        return R.ok(bookService.createBook(entity));
    }

    @Operation(summary = "编辑作品")
    @RequiresPermissions("content:book:edit")
    @Log(title = "作品管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{bookId}")
    public R<Boolean> update(@PathVariable Long bookId, @RequestBody Book entity) {
        entity.setId(bookId);
        return R.ok(bookService.updateBook(entity));
    }

    @Operation(summary = "删除作品")
    @RequiresPermissions("content:book:remove")
    @Log(title = "作品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bookId}")
    public R<Boolean> delete(@PathVariable Long bookId) {
        return R.ok(bookService.deleteBook(bookId));
    }

    @Operation(summary = "热门作品榜", description = "按字数倒序取前 N")
    @RequiresPermissions("content:book:list")
    @GetMapping("/hot")
    public R<List<BookVO>> hot(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(bookService.hotBooks(limit));
    }

    @Operation(summary = "上架", description = "status → 1，对读者可见")
    @RequiresPermissions("content:book:edit")
    @Log(title = "作品管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{bookId}/online")
    public R<Boolean> online(@PathVariable Long bookId) {
        return R.ok(bookService.online(bookId));
    }

    @Operation(summary = "下架", description = "status → 2")
    @RequiresPermissions("content:book:edit")
    @Log(title = "作品管理", businessType = BusinessType.UPDATE)
    @PostMapping("/{bookId}/offline")
    public R<Boolean> offline(@PathVariable Long bookId) {
        return R.ok(bookService.offline(bookId));
    }
}

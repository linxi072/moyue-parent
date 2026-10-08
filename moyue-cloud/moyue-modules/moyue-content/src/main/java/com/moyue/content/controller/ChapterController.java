package com.moyue.content.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.result.R;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.security.context.UserContext;
import com.moyue.content.domain.dto.query.ChapterQuery;
import com.moyue.content.domain.entity.Chapter;
import com.moyue.content.domain.vo.ChapterVO;
import com.moyue.content.service.ChapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节消费（C 端）：目录 / 详情 / 草稿 / 发布 / 排序。
 *
 * <p>写操作身份取自 {@link UserContext}（由 JWT 解析，cloud 走网关、boot 走本地过滤器），
 * 不接受前端传入作者 ID，防伪造。
 *
 * @author moyue
 */
@Tag(name = "章节消费", description = "章节目录/详情/草稿/发布/排序")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @Operation(summary = "章节详情（含正文）")
    @GetMapping("/{chapterId}")
    public R<ChapterVO> detail(@PathVariable Long chapterId) {
        return R.ok(chapterService.detail(chapterId));
    }

    @Operation(summary = "章节目录分页")
    @GetMapping
    public R<?> page(ChapterQuery query) {
        return R.ok(chapterService.pageChapters(query));
    }

    @Operation(summary = "草稿箱（status=0）")
    @GetMapping("/drafts")
    public R<List<ChapterVO>> drafts(@RequestParam Long bookId) {
        return R.ok(chapterService.catalog(bookId, 0));
    }

    @Operation(summary = "新建章节（默认草稿，序号自动排定）")
    @PostMapping
    public R<Long> create(@RequestBody Chapter entity) {
        requireLogin();
        return R.ok(chapterService.createChapter(entity));
    }

    @Operation(summary = "编辑章节")
    @PutMapping("/{chapterId}")
    public R<Boolean> update(@PathVariable Long chapterId, @RequestBody Chapter entity) {
        requireLogin();
        entity.setId(chapterId);
        return R.ok(chapterService.updateChapter(entity));
    }

    @Operation(summary = "发布 / 定时发布")
    @PostMapping("/{chapterId}/publish")
    public R<ChapterVO> publish(@PathVariable Long chapterId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishTime) {
        requireLogin();
        return R.ok(chapterService.publish(chapterId, publishTime));
    }

    @Operation(summary = "章节排序（改 chapterNo，与目标互换）")
    @PutMapping("/{chapterId}/order")
    public R<Boolean> reorder(@PathVariable Long chapterId, @RequestParam int no) {
        requireLogin();
        return R.ok(chapterService.reorder(chapterId, no));
    }

    @Operation(summary = "删除章节（逻辑删除）")
    @DeleteMapping("/{chapterId}")
    public R<Boolean> delete(@PathVariable Long chapterId) {
        requireLogin();
        return R.ok(chapterService.deleteChapter(chapterId));
    }

    private void requireLogin() {
        if (UserContext.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}

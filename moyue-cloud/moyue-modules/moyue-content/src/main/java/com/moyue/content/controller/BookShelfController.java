package com.moyue.content.controller;

import com.moyue.common.core.constant.Constants;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.R;
import com.moyue.common.security.context.UserContext;
import com.moyue.content.domain.dto.query.BookShelfQuery;
import com.moyue.content.domain.vo.ReadProgressVO;
import com.moyue.content.service.BookShelfService;
import com.moyue.content.service.ReadProgressService;
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
 * 书架与阅读进度（C 端读者）。
 *
 * <p>所有身份取自 {@link UserContext}（网关注入头派生），书架操作归属当前读者，
 * 不接受前端传入 userId（架构缺口补齐说明 7.3 书架幂等约定）。
 *
 * @author moyue
 */
@Tag(name = "书架与阅读进度", description = "加入/移出书架、列表、进度回写")
@Validated
@RestController
@RequestMapping(Constants.API_PREFIX + "/read/bookshelf")
@RequiredArgsConstructor
public class BookShelfController {

    private final BookShelfService shelfService;
    private final ReadProgressService progressService;

    @Operation(summary = "我的书架列表")
    @GetMapping
    public R<?> list(BookShelfQuery query) {
        Long userId = currentUser();
        return R.ok(shelfService.listShelf(userId, query));
    }

    @Operation(summary = "加入书架（幂等）")
    @PostMapping
    public R<Boolean> add(@RequestBody AddShelfBody body) {
        shelfService.addToShelf(currentUser(), body.bookId());
        return R.ok(true);
    }

    @Operation(summary = "移出书架")
    @DeleteMapping("/{bookId}")
    public R<Boolean> remove(@PathVariable Long bookId) {
        shelfService.removeFromShelf(currentUser(), bookId);
        return R.ok(true);
    }

    @Operation(summary = "回写阅读进度（同时同步书架最近章节）")
    @PutMapping("/{bookId}/progress")
    public R<Boolean> progress(@PathVariable Long bookId,
                              @RequestBody ProgressBody body) {
        Long userId = currentUser();
        progressService.upsert(userId, bookId, body.chapterNo(), body.position());
        shelfService.touch(userId, bookId, body.chapterNo());
        return R.ok(true);
    }

    @Operation(summary = "读取阅读进度")
    @GetMapping("/{bookId}/progress")
    public R<ReadProgressVO> getProgress(@PathVariable Long bookId) {
        return R.ok(progressService.get(currentUser(), bookId));
    }

    private Long currentUser() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    /** 加入书架入参 */
    public record AddShelfBody(Long bookId) {
    }

    /** 进度回写入参 */
    public record ProgressBody(int chapterNo, int position) {
    }
}

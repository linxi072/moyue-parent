package com.moyue.chapter.controller;

import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.constants.Constants;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 章节接口：章节详情 / 目录分页 / 作者写作链路（草稿箱、增删改、定时发布、排序）。
 * 路径前缀 /api/v1 与网关路由、Feign ChapterClient 保持一致。
 * 写接口的归属校验取网关注入的 X-User-Id / X-User-Role。
 */
@RestController
@RequestMapping("/api/v1")
public class ChapterController {

    @Autowired
    private ChapterService chapterService;

    /**
     * 章节正文（供 Feign ChapterClient 调用，路径必须为 /api/v1/chapters/{chapterId}）。
     * 16-11：返回 DTO 而非实体，与 ChapterClient 声明的 {@code R<ChapterDTO>} 对齐；
     * 单章接口填充 content，列表接口不留正文（见 {@link ChapterService#toDtoLite}）。
     */
    @GetMapping("/chapters/{chapterId}")
    public R<ChapterDTO> getChapter(@PathVariable Long chapterId) {
        ChapterEntity chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return R.ok(ChapterService.toDto(chapter));
    }

    /** 作品目录分页（按 bookId 查询；列表口径不含正文） */
    @GetMapping("/chapters")
    public R<PageResult<ChapterDTO>> listChapters(@RequestParam Long bookId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return R.ok(ChapterService.toDtoPage(chapterService.listByBook(bookId, page, size)));
    }

    /** 草稿箱：按作品查 status=0 的章节（列表口径不含正文） */
    @GetMapping("/chapters/drafts")
    public R<PageResult<ChapterDTO>> listDrafts(@RequestParam Long bookId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return R.ok(ChapterService.toDtoPage(chapterService.listDrafts(bookId, page, size)));
    }

    /** 创建章节（草稿 / 直接提交审核） */
    @PostMapping("/chapters")
    public R<ChapterDTO> createChapter(@RequestBody CreateChapterRequest req, HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(ChapterService.toDto(chapterService.createChapter(userId, role, req.getBookId(), req.getTitle(),
                req.getContent(), req.getChapterNo(), req.getStatus())));
    }

    /** 编辑章节 */
    @PutMapping("/chapters/{chapterId}")
    public R<ChapterDTO> updateChapter(@PathVariable Long chapterId,
                                       @RequestBody UpdateChapterRequest req,
                                       HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(ChapterService.toDto(chapterService.updateChapter(chapterId, userId, role, req.getTitle(),
                req.getContent(), req.getChapterNo(), req.getStatus(), req.getPublishTime())));
    }

    /** 删除章节（逻辑删除） */
    @DeleteMapping("/chapters/{chapterId}")
    public R<Void> deleteChapter(@PathVariable Long chapterId, HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        chapterService.deleteChapter(chapterId, userId, role);
        return R.ok();
    }

    /** 发布 / 定时发布：publishTime 为空或已过则立即发布，否则置为审核中（定时） */
    @PostMapping("/chapters/{chapterId}/publish")
    public R<ChapterDTO> publish(@PathVariable Long chapterId,
                                 @RequestBody(required = false) PublishRequest req,
                                 HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(ChapterService.toDto(chapterService.publish(chapterId, userId, role,
                req == null ? null : req.getPublishTime())));
    }

    /** 章节排序：修改章节序号 */
    @PutMapping("/chapters/{chapterId}/order")
    public R<ChapterDTO> reorder(@PathVariable Long chapterId,
                                 @RequestBody OrderRequest req,
                                 HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(ChapterService.toDto(chapterService.reorder(chapterId, userId, role, req.getChapterNo())));
    }

    // ------------------------------ 上下文工具 ------------------------------

    private long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    private int currentRole(HttpServletRequest request) {
        String role = request.getHeader(Constants.USER_ROLE_HEADER);
        if (role == null || role.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(role.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------ 请求体 ------------------------------

    /** 创建章节请求 */
    @Data
    public static class CreateChapterRequest {
        private Long bookId;
        private String title;
        private String content;
        private Integer chapterNo;
        /** 0 草稿 / 1 审核中；为空默认 0 草稿 */
        private Integer status;
    }

    /** 编辑章节请求（字段均可选，仅更新非空字段） */
    @Data
    public static class UpdateChapterRequest {
        private String title;
        private String content;
        private Integer chapterNo;
        private Integer status;
        private java.time.LocalDateTime publishTime;
    }

    /** 发布请求（可为空 body，表示立即发布） */
    @Data
    public static class PublishRequest {
        private java.time.LocalDateTime publishTime;
    }

    /** 排序请求 */
    @Data
    public static class OrderRequest {
        private Integer chapterNo;
    }
}

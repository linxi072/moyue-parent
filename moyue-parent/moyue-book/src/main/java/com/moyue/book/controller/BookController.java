package com.moyue.book.controller;

import com.moyue.api.dto.BookSummaryDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.book.service.BookService;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
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
 * 书城接口：书籍分页 / 详情 / 作品管理（创建 / 编辑 / 删除）。
 * 返回共享 DTO BookSummaryDTO，与 Feign BookClient 及前端契约保持一致。
 * 写接口的 authorId 一律取网关注入的 X-User-Id，杜绝前端伪造。
 */
@RestController
@RequestMapping("/api/v1")
public class BookController {

    @Autowired
    private BookService bookService;

    /** 分页查询书籍（读取网关注入的 X-User-Id 上下文头） */
    @GetMapping("/books")
    public R<PageResult<BookSummaryDTO>> listBooks(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   HttpServletRequest request) {
        String userId = request.getHeader(Constants.USER_ID_HEADER);
        // userId 非空代表请求已通过网关鉴权
        return R.ok(bookService.listBooks(page, size));
    }

    /** 书籍详情 */
    @GetMapping("/books/{bookId}")
    public R<BookSummaryDTO> detail(@PathVariable Long bookId) {
        BookSummaryDTO dto = bookService.detail(bookId);
        if (dto == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        return R.ok(dto);
    }

    /** 创建作品（作者 / 管理员） */
    @PostMapping("/books")
    public R<BookSummaryDTO> createBook(@RequestBody CreateBookRequest req, HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(bookService.createBook(userId, role, req.getTitle(), req.getCoverUrl(),
                req.getCategoryId(), req.getTags(), req.getIntro()));
    }

    /** 编辑作品（作者本人 / 管理员） */
    @PutMapping("/books/{bookId}")
    public R<BookSummaryDTO> updateBook(@PathVariable Long bookId,
                                        @RequestBody UpdateBookRequest req,
                                        HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        return R.ok(bookService.updateBook(bookId, userId, role, req.getTitle(), req.getCoverUrl(),
                req.getCategoryId(), req.getTags(), req.getIntro(), req.getStatus()));
    }

    /** 删除作品（逻辑删除；作者本人 / 管理员） */
    @DeleteMapping("/books/{bookId}")
    public R<Void> deleteBook(@PathVariable Long bookId, HttpServletRequest request) {
        long userId = requireUserId(request);
        int role = currentRole(request);
        bookService.deleteBook(bookId, userId, role);
        return R.ok();
    }

    // ------------------------------ 上下文工具 ------------------------------

    /** 从网关注入头取当前用户 ID；缺失 / 非法 → 10002 未登录 */
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

    /** 从网关注入头取当前角色；缺失 / 非法按 0 处理（1 读者 / 2 作者 / 3 管理员） */
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

    /** 创建作品请求 */
    @Data
    public static class CreateBookRequest {
        private String title;
        private String coverUrl;
        private Long categoryId;
        private String tags;
        private String intro;
    }

    /** 编辑作品请求（字段均可选，仅更新非空字段；status 可切换连载 / 完结 / 下架） */
    @Data
    public static class UpdateBookRequest {
        private String title;
        private String coverUrl;
        private Long categoryId;
        private String tags;
        private String intro;
        private Integer status;
    }
}

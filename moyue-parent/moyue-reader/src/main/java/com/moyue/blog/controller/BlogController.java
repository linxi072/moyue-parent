package com.moyue.blog.controller;

import com.moyue.api.dto.BlogCommentDTO;
import com.moyue.api.dto.BlogPostDTO;
import com.moyue.api.dto.PageResult;
import com.moyue.blog.service.BlogService;
import com.moyue.common.R;
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

import lombok.Data;

/**
 * 博客空间接口：文章 / 评论 / 点赞。
 * 路径前缀 /api/v1 与网关路由、Feign BlogClient 保持一致。
 */
@RestController
@RequestMapping("/api/v1")
public class BlogController {

    @Autowired
    private BlogService blogService;

    /** 新建文章 */
    @PostMapping("/blog/posts")
    public R<BlogPostDTO> createPost(@RequestBody CreatePostRequest req) {
        return R.ok(blogService.createPost(req.getAuthorId(), req.getTitle(), req.getCoverUrl(),
                req.getSummary(), req.getContent(), req.getStatus()));
    }

    /** 文章列表（authorId 可选；默认第 1 页、每页 20 条） */
    @GetMapping("/blog/posts")
    public R<PageResult<BlogPostDTO>> listPosts(@RequestParam(required = false) Long authorId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return R.ok(blogService.listPosts(authorId, page, size));
    }

    /** 文章详情（浏览数 +1） */
    @GetMapping("/blog/posts/{id}")
    public R<BlogPostDTO> getPost(@PathVariable Long id) {
        return R.ok(blogService.getPost(id));
    }

    /** 更新文章字段 */
    @PutMapping("/blog/posts/{id}")
    public R<BlogPostDTO> updatePost(@PathVariable Long id, @RequestBody UpdatePostRequest req) {
        return R.ok(blogService.updatePost(id, req.getTitle(), req.getCoverUrl(),
                req.getSummary(), req.getContent(), req.getStatus()));
    }

    /** 新增评论 */
    @PostMapping("/blog/posts/{id}/comments")
    public R<BlogCommentDTO> addComment(@PathVariable Long id, @RequestBody CreateCommentRequest req) {
        return R.ok(blogService.addComment(id, req.getUserId(), req.getContent()));
    }

    /** 评论列表（默认第 1 页、每页 20 条） */
    @GetMapping("/blog/posts/{id}/comments")
    public R<PageResult<BlogCommentDTO>> listComments(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(blogService.listComments(id, page, size));
    }

    /** 点赞切换（返回当前 like_count） */
    @PostMapping("/blog/posts/{id}/like")
    public R<Integer> toggleLike(@PathVariable Long id, @RequestBody LikeRequest req) {
        return R.ok(blogService.toggleLike(id, req.getUserId()));
    }

    /** 逻辑删除文章 */
    @DeleteMapping("/blog/posts/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        blogService.deletePost(id);
        return R.ok();
    }

    // ------------------------------ 请求体 ------------------------------

    /** 新建文章请求 */
    @Data
    public static class CreatePostRequest {
        private Long authorId;
        private String title;
        private String coverUrl;
        private String summary;
        private String content;
        private Integer status;
    }

    /** 更新文章请求 */
    @Data
    public static class UpdatePostRequest {
        private String title;
        private String coverUrl;
        private String summary;
        private String content;
        private Integer status;
    }

    /** 新增评论请求 */
    @Data
    public static class CreateCommentRequest {
        private Long userId;
        private String content;
    }

    /** 点赞请求 */
    @Data
    public static class LikeRequest {
        private Long userId;
    }
}

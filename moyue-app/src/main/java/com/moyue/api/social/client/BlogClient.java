package com.moyue.api.social.client;

import com.moyue.api.social.dto.BlogPostDTO;
import com.moyue.blog.service.BlogService;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.PageResult;
import org.springframework.stereotype.Component;

/**
 * 博客服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-social) 已移除 OpenFeign，改为直接注入 {@link BlogService} 委托调用。
 */
@Component
public class BlogClient {

    private final BlogService blogService;

    public BlogClient(BlogService blogService) {
        this.blogService = blogService;
    }

    /** 按作者分页查询博客文章 */
    public R<PageResult<BlogPostDTO>> listPosts(Long authorId, int page, int size) {
        try {
            return R.ok(blogService.listPosts(authorId, page, size));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

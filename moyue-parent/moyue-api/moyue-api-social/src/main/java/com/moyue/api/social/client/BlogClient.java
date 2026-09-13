package com.moyue.api.social.client;

import com.moyue.api.social.dto.BlogPostDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 博客服务 Feign 客户端（moyue-social）。
 * 返回类型包裹 R&lt;T&gt;，与控制器实际响应结构一致。
 */
@FeignClient(name = "moyue-social", fallbackFactory = BlogClientFallbackFactory.class)
public interface BlogClient {

    /** 按作者分页查询博客文章 */
    @GetMapping("/api/v1/blog/posts")
    R<PageResult<BlogPostDTO>> listPosts(@RequestParam("authorId") Long authorId,
                                         @RequestParam("page") int page,
                                         @RequestParam("size") int size);
}

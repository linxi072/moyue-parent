package com.moyue.api.content.client;

import com.moyue.api.content.dto.BookshelfSummaryDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 书架服务 Feign 客户端（moyue-content）。
 *
 * <p>供 moyue-search 的个性化推荐构建用户兴趣画像——取某用户书架上的书籍 ID 列表。
 * contextId 与同服务的 {@link BookClient}/{@link ChapterClient} 区分注册，避免
 * FeignClientSpecification 同名 bean 冲突。</p>
 */
@FeignClient(name = "moyue-content", contextId = "bookshelfClient", fallbackFactory = BookshelfClientFallbackFactory.class)
public interface BookshelfClient {

    /**
     * 取用户书架书籍 ID 列表（内部端点，不经网关，仅服务间调用）。
     *
     * @param userId 用户 ID
     * @return 书架书籍概要列表（仅含 bookId）
     */
    @GetMapping("/api/v1/internal/bookshelf/{userId}")
    R<List<BookshelfSummaryDTO>> getBookshelf(@PathVariable("userId") Long userId);
}

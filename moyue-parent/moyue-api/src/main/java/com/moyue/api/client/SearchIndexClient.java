package com.moyue.api.client;

import com.moyue.api.dto.BookIndexDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 检索服务 Feign 客户端（moyue-search）。
 * 返回类型包裹 R&lt;T&gt;，与 SearchInternalController 内部端点结构一致。
 * 供 moyue-content 在书籍创建 / 更新 / 下架时同步 ES 索引（P2-13）。
 */
@FeignClient(name = "moyue-reader")
public interface SearchIndexClient {

    /** 索引一本书（bookId 为 _id，重复推送覆盖更新，幂等） */
    @PostMapping("/api/v1/internal/search/books/_index")
    R<BookIndexDTO> indexBook(@RequestBody BookIndexDTO book);

    /** 下架同步：物理删除索引文档 */
    @DeleteMapping("/api/v1/internal/search/books/{bookId}")
    R<Void> removeBook(@PathVariable("bookId") Long bookId);
}

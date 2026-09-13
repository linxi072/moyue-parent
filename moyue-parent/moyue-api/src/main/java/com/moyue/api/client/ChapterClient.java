package com.moyue.api.client;

import com.moyue.api.dto.ChapterDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 章节服务 Feign 客户端（moyue-content）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<ChapterDTO>} 结构一致。
 */
@FeignClient(name = "moyue-author")
public interface ChapterClient {

    /** 章节正文 */
    @GetMapping("/api/v1/chapters/{chapterId}")
    R<ChapterDTO> getChapter(@PathVariable("chapterId") Long chapterId);

    /**
     * 审核回写章节状态（内部端点，不经网关）：status 2=已发布 / 3=已驳回。
     * 供 moyue-platform 审核裁决后调用。
     */
    @PutMapping("/api/v1/internal/chapters/{chapterId}/audit")
    R<Void> auditChapter(@PathVariable("chapterId") Long chapterId,
                         @RequestParam("status") Integer status);
}

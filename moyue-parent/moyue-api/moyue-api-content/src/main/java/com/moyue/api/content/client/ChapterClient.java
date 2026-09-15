package com.moyue.api.content.client;

import com.moyue.api.content.dto.ChapterDTO;
import com.moyue.api.search.dto.ChapterIndexDTO;
import com.moyue.common.R;
import com.moyue.common.core.domain.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 章节服务 Feign 客户端（moyue-content）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<ChapterDTO>} 结构一致。
 */
/**
 * 章节服务 Feign 客户端（moyue-content）。
 * 返回类型包裹 R&lt;T&gt;，与控制器 {@code R<ChapterDTO>} 结构一致。
 *
 * <p>contextId：与同服务的 BookClient（name 同为 moyue-content）区分注册，
 * 避免 FeignClientSpecification 同名 bean 冲突（正解，替代 allow-bean-definition-overriding）。</p>
 */
@FeignClient(name = "moyue-content", contextId = "chapterClient", fallbackFactory = ChapterClientFallbackFactory.class)
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

    /**
     * 分页拉取全量已发布章节（内部端点，不经网关，仅服务间调用）：
     * 返回含正文（已截断至前 20000 字符）的索引载荷，按 chapter.id 升序；
     * 供 moyue-search 管理端全量重建 moyue-chapter 索引。
     */
    @GetMapping("/api/v1/internal/chapter/page")
    R<PageResult<ChapterIndexDTO>> pageChapters(@RequestParam("page") int page,
                                                @RequestParam("size") int size);

    /**
     * 激活到点的定时章节（内部端点，不经网关）：status 4（定时待发布）→ 2（已发布）并同步 ES 索引（P0-2）。
     * 供 {@code ChapterPublishJobHandler}（moyue-system）周期调用；
     * 幂等——条件更新自带 status=4 限定，已发布章节自然跳过，重复调度不重复建索引。
     *
     * @return 本次实际激活的章节条数
     */
    @PutMapping("/api/v1/internal/chapters/activate-scheduled")
    R<Integer> activateScheduledChapters();
}

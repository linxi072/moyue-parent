package com.moyue.author.center;

import lombok.Data;

import java.util.List;

/**
 * 批量操作统一返回：成功标记、成功处理的章节 ID、失败的章节 ID。
 * 三个批量端点（删除 / 发布 / 改状态）共用本结构；publishedIds 语义为「成功处理的章节 ID」。
 */
@Data
public class BatchResult {

    /** 是否全部成功（任一失败则抛 {@link BatchOperationException}，不会返回 success=false） */
    private boolean success;

    /** 成功处理的章节 ID（发布 / 删除 / 改状态均复用该字段） */
    private List<Long> publishedIds;

    /** 失败的章节 ID（任一失败即整批回滚，列表回传调用方） */
    private List<Long> failedIds;
}

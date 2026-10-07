package com.moyue.author.center;

import java.util.Collections;
import java.util.List;

/**
 * 批量操作异常：承载批量执行中失败的章节 ID 列表。
 * 继承 {@link RuntimeException}，触发调用方 {@code @Transactional} 整批回滚；
 * 控制器捕获后由 {@code AuthorCenterController#batchFail} 封装为 {@code R<BatchResult>}(success=false, failedIds=列表)。
 */
public class BatchOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 执行失败的章节 ID 列表 */
    private final List<Long> failedIds;

    public BatchOperationException(List<Long> failedIds) {
        super("批量操作部分失败，已整批回滚");
        this.failedIds = failedIds == null ? Collections.emptyList() : failedIds;
    }

    /** @return 失败的章节 ID 列表（不可变视图由调用方保证） */
    public List<Long> getFailedIds() {
        return failedIds;
    }
}

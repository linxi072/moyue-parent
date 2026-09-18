package com.moyue.common.core.domain;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 游标分页结果（关注列表 / 粉丝列表 / 动态时间线 / 作者主页动态通用）。
 *
 * <p>排序键唯一化：{@code (create_time DESC, id DESC)}，cursor 编码为 URL-safe 串
 * {@code createTimeMillis_id}。不使用 OFFSET 深翻页，避免大列表性能与重复 / 遗漏。</p>
 *
 * @param <T> 记录类型
 */
@Data
public class CursorPageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;

    /** 下一页游标（null / 空表示无更多）；取本页最后一条记录的 (createTimeMillis_id) */
    private String nextCursor;

    /** 是否还有更多 */
    private boolean hasMore;
}

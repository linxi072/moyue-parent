package com.moyue.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果：入参 page / size，出参 total + records（架构说明书 11.2）。
 *
 * <p>刻意不依赖 MyBatis-Plus 的 IPage，保持 common-core 轻量；
 * 与 IPage 的桥接由 common-mybatis 提供。
 *
 * @author moyue
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private long total;

    /** 当前页数据 */
    private List<T> records;

    public static <T> PageResult<T> of(long total, List<T> records) {
        return new PageResult<>(total, records == null ? Collections.emptyList() : records);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, Collections.emptyList());
    }

    /** 数据行转换（Entity -> DTO），保持分页信息不变 */
    public <V> PageResult<V> map(Function<? super T, ? extends V> mapper) {
        List<V> list = this.records.stream()
                .map(mapper::apply)
                .collect(Collectors.toList());
        return new PageResult<>(this.total, list);
    }
}

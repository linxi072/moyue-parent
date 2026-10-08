package com.moyue.common.mybatis.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.moyue.common.core.result.PageQuery;
import com.moyue.common.core.result.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * 分页工具：MyBatis-Plus 的 IPage → 统一响应体 PageResult。
 *
 * <p>common-core 刻意不依赖 mybatis-plus（保持轻量），桥接放在 common-mybatis，
 * 所有业务模块统一复用，避免各模块重复一份 PageUtils。</p>
 *
 * @author moyue
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 构造 MP 分页对象。
     *
     * @param query 分页查询条件
     * @param <T>   实体类型
     * @return MP 分页对象
     */
    public static <T> com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page(PageQuery query) {
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                query.getPage(), query.getSize());
    }

    /**
     * IPage 转 PageResult（不做行转换）。
     *
     * @param page MP 分页结果
     * @param <T>  行类型
     * @return 统一分页响应
     */
    public static <T> PageResult<T> toResult(IPage<T> page) {
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * IPage 转 PageResult（Entity → VO）。
     *
     * @param page       MP 分页结果
     * @param converter  行转换器
     * @param <E>        实体类型
     * @param <V>        视图类型
     * @return 统一分页响应
     */
    public static <E, V> PageResult<V> toResult(IPage<E> page, Function<E, V> converter) {
        List<V> records = page.getRecords().stream().map(converter).toList();
        return PageResult.of(page.getTotal(), records);
    }
}

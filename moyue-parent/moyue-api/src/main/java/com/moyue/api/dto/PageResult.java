package com.moyue.api.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 分页结果（跨服务共享）：total / page / size / records。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    private long total;

    /** 当前页 */
    private int page;

    /** 每页大小 */
    private int size;

    /** 当前页数据 */
    private List<T> records;
}

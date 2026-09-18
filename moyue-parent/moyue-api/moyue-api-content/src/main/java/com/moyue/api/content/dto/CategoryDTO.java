package com.moyue.api.content.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 分类 DTO（跨服务共享）：moyue-content 推送 / 返回的分类数据，与 {@code CategoryController}
 * HTTP 契约一致。字段对齐 category 表核心列，供书城筛选项与后台管理复用。
 */
@Data
public class CategoryDTO implements Serializable {

    /** 分类 ID → category.id */
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类图标 */
    private String icon;

    /** 显示顺序（升序） */
    private Integer sort;
}

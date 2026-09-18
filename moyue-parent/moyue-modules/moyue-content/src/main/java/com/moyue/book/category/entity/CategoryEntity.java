package com.moyue.book.category.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小说分类实体，映射 category 表（P2-A 分类服务独立化）。
 *
 * <p>运营后台可对分类增删改排序；分类名经 {@code CategoryService} 解析，替代原
 * {@code BookService} 的 {@code CATEGORY_NAMES} 硬编码字典。</p>
 *
 * <p>逻辑删除：本项目 application.yml 的全局 logic-delete-field 处于注释状态，
 * 故每个软删实体必须显式 {@code @TableLogic}，否则会静默退化为物理 DELETE。</p>
 */
@Data
@TableName("category")
public class CategoryEntity {

    /** 分类主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类图标（前端展示用，可空） */
    private String icon;

    /** 显示顺序（升序：书城筛选项排序） */
    private Integer sort;

    /** 状态：0 禁用 / 1 正常（可空，默认 1） */
    private Integer status;

    /** 逻辑删除：0 否 / 1 是（显式 @TableLogic，防止退化为物理 DELETE） */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

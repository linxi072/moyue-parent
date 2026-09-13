package com.moyue.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品实体，映射 book 表。
 */
@Data
@TableName("book")
public class BookEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作者 ID → user.id */
    private Long authorId;

    /** 作品名称 */
    private String title;

    /** 封面地址 */
    private String coverUrl;

    /** 分类 ID（演示字典映射为分类名） */
    private Long categoryId;

    /** 标签，逗号分隔 */
    private String tags;

    /** 作品简介 */
    private String intro;

    /** 状态：1 连载中 / 2 已完结 / 3 已下架 */
    private Integer status;

    /** 累计字数 */
    private Integer wordCount;

    /** 累计点击 */
    private Long clickCount;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

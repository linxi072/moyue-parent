package com.moyue.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 作品（小说）实体。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_book")
public class Book extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 书名 */
    private String title;

    /** 作者名 */
    private String authorName;

    /** 分类（→ sys_dict_data，dict_type = book_category） */
    private Long categoryId;

    /** 状态：0 连载中 / 1 已完结 / 2 已下架 */
    private Integer status;

    /** 总字数 */
    private Long wordCount;

    /** 简介 */
    private String intro;

    /** 封面 URL */
    private String coverUrl;
}

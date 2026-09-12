package com.moyue.read.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书架实体，映射 bookshelf 表（用户与书籍多对多）。
 */
@Data
@TableName("bookshelf")
public class BookshelfEntity {

    /** 书架记录主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 读者 ID → user.id */
    private Long userId;

    /** 书籍 ID → book.id */
    private Long bookId;

    /** 最后阅读章节 → chapter.id */
    private Long lastChapterId;

    /** 逻辑删除：0 否 / 1 是 */
    private Integer isDeleted;

    /** 加入书架时间 */
    private LocalDateTime createTime;
}

package com.moyue.chapter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节实体，映射 chapter 表。
 * 注意：不含 content 正文大字段，避免详情接口无谓返回正文。
 */
@Data
@TableName("chapter")
public class ChapterEntity {

    /** 章节主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作品 ID → book.id */
    private Long bookId;

    /** 章节序号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String title;

    /** 章节正文（chapter 表 MEDIUMTEXT；创作链路写入，目录列表亦返回） */
    private String content;

    /** 本章字数 */
    private Integer wordCount;

    /** 状态：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回 */
    private Integer status;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;
}

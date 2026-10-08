package com.moyue.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 章节实体。
 *
 * <p>生命周期：草稿(0) → 发布(1) / 定时发布(2)。序号 {@code chapterNo} 由发布逻辑
 * 取该作品 max+1 自动排定，编辑时不允许手动篡改以保证目录连续。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_chapter")
public class Chapter extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属作品 */
    private Long bookId;

    /** 章节序号，从 1 递增 */
    private Integer chapterNo;

    /** 章节标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 本章字数 */
    private Integer wordCount;

    /** 状态：0 草稿 / 1 已发布 / 2 定时发布 */
    private Integer status;

    /** 发布时间（定时发布时为计划时间） */
    private LocalDateTime publishTime;
}

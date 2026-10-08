package com.moyue.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 阅读进度实体（跨端回写）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_read_progress")
public class ReadProgress extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 读者 ID */
    private Long userId;

    /** 作品 ID */
    private Long bookId;

    /** 最近阅读章节号 */
    private Integer chapterNo;

    /** 段内位置（字符偏移） */
    private Integer position;
}

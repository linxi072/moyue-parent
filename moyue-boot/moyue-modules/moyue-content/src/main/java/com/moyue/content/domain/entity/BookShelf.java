package com.moyue.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 书架实体（读者收藏）。
 *
 * <p>同一 (user_id, book_id) 仅一行：移出仅置 {@code isDeleted=1}，重新加入时「复活」
 * 该行（见 V29 注释与架构缺口补齐说明 7.3 书架幂等约定）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_bookshelf")
public class BookShelf extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 读者 ID（身份来自网关注入头，不允许前端传入） */
    private Long userId;

    /** 作品 ID */
    private Long bookId;

    /** 最近阅读章节号 */
    private Integer lastChapterNo;
}

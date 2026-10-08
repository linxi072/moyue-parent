package com.moyue.content.domain.dto.query;

import com.moyue.common.core.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 书架分页查询（读者维度，user_id 取当前登录者）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookShelfQuery extends PageQuery {

    /** 作品名模糊（关联 moyue_book.title） */
    private String bookTitle;
}

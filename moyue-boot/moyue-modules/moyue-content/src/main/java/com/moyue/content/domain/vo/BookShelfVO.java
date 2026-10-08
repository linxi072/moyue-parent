package com.moyue.content.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书架视图（聚合作品基础信息）。
 *
 * @author moyue
 */
@Data
@Builder
public class BookShelfVO {

    private Long id;

    /** 书架所属用户；C 端列表来自登录态，管理端全局查询需显式返回 */
    private Long userId;

    private Long bookId;
    private String bookTitle;
    private String coverUrl;
    private Integer lastChapterNo;
    private LocalDateTime createTime;
}

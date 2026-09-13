package com.moyue.api.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 机审请求 DTO（跨服务共享）：内容域 / 社区域提交待审内容给 moyue-risk。
 */
@Data
public class ModerationRequestDTO implements Serializable {

    /** 业务类型：1 章节 / 2 评论 / 3 书籍 */
    private Integer bizType;

    /** 业务主键 */
    private Long bizId;

    /** 标题（可空） */
    private String title;

    /** 正文 / 评论内容 */
    private String content;
}

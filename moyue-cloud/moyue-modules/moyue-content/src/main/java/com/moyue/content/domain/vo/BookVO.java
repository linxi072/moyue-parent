package com.moyue.content.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作品视图。
 *
 * @author moyue
 */
@Data
@Builder
public class BookVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String authorName;
    private Long categoryId;
    private Integer status;
    private Long wordCount;
    private String intro;
    private String coverUrl;
    private LocalDateTime createTime;
    private String remark;
}

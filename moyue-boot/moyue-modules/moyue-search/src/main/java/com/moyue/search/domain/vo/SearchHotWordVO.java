package com.moyue.search.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搜索热词视图。
 *
 * @author moyue
 */
@Data
@Builder
public class SearchHotWordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String word;
    private Integer hitCount;
    private Integer weight;
    private Integer enabled;
    private LocalDateTime createTime;
    private String remark;
}

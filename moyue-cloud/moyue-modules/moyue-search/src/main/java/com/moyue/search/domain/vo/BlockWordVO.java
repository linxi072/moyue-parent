package com.moyue.search.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搜索屏蔽词视图。
 *
 * @author moyue
 */
@Data
@Builder
public class BlockWordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String word;
    private Integer level;
    private Integer enabled;
    private Integer hitCount;
    private LocalDateTime createTime;
    private String remark;
}

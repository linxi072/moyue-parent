package com.moyue.risk.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感词视图。
 *
 * @author moyue
 */
@Data
@Builder
public class SensitiveWordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String word;
    private Integer level;
    private Integer enabled;
    private Integer hitCount;
    private LocalDateTime createTime;
    private String remark;
}

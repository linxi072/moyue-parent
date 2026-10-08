package com.moyue.system.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型视图。
 *
 * @author moyue
 */
@Data
@Builder
public class DictTypeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String dictName;
    private String dictType;
    private Integer status;
    private LocalDateTime createTime;
    private String remark;
}

package com.moyue.system.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典数据视图。
 *
 * @author moyue
 */
@Data
@Builder
public class DictDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer dictSort;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createTime;
    private String remark;
}

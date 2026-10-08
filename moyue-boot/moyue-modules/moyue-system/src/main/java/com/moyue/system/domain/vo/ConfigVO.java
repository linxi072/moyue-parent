package com.moyue.system.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 参数配置视图。
 *
 * @author moyue
 */
@Data
@Builder
public class ConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String configName;
    private String configKey;
    private String configValue;
    private Integer configType;
    private LocalDateTime createTime;
    private String remark;
}

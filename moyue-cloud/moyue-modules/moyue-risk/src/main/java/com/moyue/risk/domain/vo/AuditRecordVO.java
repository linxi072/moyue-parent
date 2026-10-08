package com.moyue.risk.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审核工单视图。
 *
 * @author moyue
 */
@Data
@Builder
public class AuditRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer bizType;
    private String bizId;
    private String content;
    private Integer status;
    private String auditor;
    private String reason;
    private LocalDateTime createTime;
    private String remark;
}

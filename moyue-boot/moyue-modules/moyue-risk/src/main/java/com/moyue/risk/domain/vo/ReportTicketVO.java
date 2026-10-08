package com.moyue.risk.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报工单视图。
 *
 * @author moyue
 */
@Data
@Builder
public class ReportTicketVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer bizType;
    private String bizId;
    private Long reporterId;
    private String reason;
    private String content;
    private Integer status;
    private String handler;
    private String handleReason;
    private LocalDateTime createTime;
    private String remark;
}

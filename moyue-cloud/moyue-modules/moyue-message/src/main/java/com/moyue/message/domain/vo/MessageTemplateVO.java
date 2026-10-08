package com.moyue.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息模板视图。
 *
 * @author moyue
 */
@Data
@Builder
public class MessageTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String title;
    private String content;
    private Integer type;
    private Integer enabled;
    private LocalDateTime createTime;
    private String remark;
}

package com.moyue.message.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信视图。
 *
 * @author moyue
 */
@Data
@Builder
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long fromUser;
    private Long toUser;
    private String title;
    private String content;
    private Integer type;
    private Integer readFlag;
    private LocalDateTime createTime;
    private String remark;
}

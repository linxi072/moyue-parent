package com.moyue.message.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.moyue.common.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 消息模板实体（群发站内信引用，支持 ${name} 占位渲染）。
 *
 * @author moyue
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moyue_message_template")
public class MessageTemplate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板编码（唯一） */
    private String code;

    /** 标题 */
    private String title;

    /** 正文（支持 ${name} 占位） */
    private String content;

    /** 类型：1 系统 / 2 活动 / 3 私信 */
    private Integer type;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}

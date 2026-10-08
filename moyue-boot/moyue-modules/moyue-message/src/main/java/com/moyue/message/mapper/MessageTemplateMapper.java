package com.moyue.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.message.domain.entity.MessageTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息模板数据访问。
 *
 * @author moyue
 */
@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplate> {
}

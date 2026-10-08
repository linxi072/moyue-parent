package com.moyue.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.message.domain.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内信数据访问。
 *
 * @author moyue
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}

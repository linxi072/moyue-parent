package com.moyue.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.social.domain.entity.ImMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * IM 消息 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface ImMessageMapper extends BaseMapper<ImMessage> {
}

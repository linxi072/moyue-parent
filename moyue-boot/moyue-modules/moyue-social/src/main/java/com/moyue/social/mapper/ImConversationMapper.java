package com.moyue.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.social.domain.entity.ImConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * IM 会话 Mapper。
 *
 * @author moyue
 */
@Mapper
public interface ImConversationMapper extends BaseMapper<ImConversation> {
}

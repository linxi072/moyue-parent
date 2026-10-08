package com.moyue.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.social.domain.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社区帖子数据访问。
 *
 * @author moyue
 */
@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
}

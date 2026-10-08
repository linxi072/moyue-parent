package com.moyue.social.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.social.domain.dto.query.CommunityPostQuery;
import com.moyue.social.domain.entity.CommunityPost;
import com.moyue.social.domain.vo.CommunityPostVO;

/**
 * 社区域服务。
 *
 * @author moyue
 */
public interface CommunityPostService {

    PageResult<CommunityPostVO> pagePosts(CommunityPostQuery query);

    Long createPost(CommunityPost entity);

    boolean updatePost(CommunityPost entity);

    boolean deletePost(Long postId);

    /** 运营加热：点赞数 +1 */
    boolean like(Long postId);
}

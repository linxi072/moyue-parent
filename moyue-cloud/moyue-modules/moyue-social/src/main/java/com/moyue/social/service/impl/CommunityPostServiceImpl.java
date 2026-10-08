package com.moyue.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.social.domain.dto.query.CommunityPostQuery;
import com.moyue.social.domain.entity.CommunityPost;
import com.moyue.social.domain.vo.CommunityPostVO;
import com.moyue.social.mapper.CommunityPostMapper;
import com.moyue.social.service.CommunityPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 社区域实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityPostServiceImpl implements CommunityPostService {

    private final CommunityPostMapper postMapper;

    @Override
    public PageResult<CommunityPostVO> pagePosts(CommunityPostQuery query) {
        var page = PageUtils.<CommunityPost>page(query);
        var result = postMapper.selectPage(page, new LambdaQueryWrapper<CommunityPost>()
                .like(StringUtils.hasText(query.getTitle()), CommunityPost::getTitle, query.getTitle())
                .like(StringUtils.hasText(query.getTopic()), CommunityPost::getTopic, query.getTopic())
                .eq(query.getStatus() != null, CommunityPost::getStatus, query.getStatus())
                .eq(query.getUserId() != null, CommunityPost::getUserId, query.getUserId())
                .orderByDesc(CommunityPost::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(CommunityPost entity) {
        if (!StringUtils.hasText(entity.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "帖子标题不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getLikeCount() == null) {
            entity.setLikeCount(0);
        }
        if (entity.getCommentCount() == null) {
            entity.setCommentCount(0);
        }
        postMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePost(CommunityPost entity) {
        CommunityPost exist = postMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("社区帖子");
        }
        return postMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePost(Long postId) {
        CommunityPost exist = postMapper.selectById(postId);
        if (exist == null) {
            throw BusinessException.notFound("社区帖子");
        }
        return postMapper.deleteById(postId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean like(Long postId) {
        CommunityPost exist = postMapper.selectById(postId);
        if (exist == null) {
            throw BusinessException.notFound("社区帖子");
        }
        return postMapper.update(null, new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, postId)
                .setSql("like_count = like_count + 1")) > 0;
    }

    private CommunityPostVO toVO(CommunityPost e) {
        return CommunityPostVO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .userName(e.getUserName())
                .title(e.getTitle())
                .content(e.getContent())
                .topic(e.getTopic())
                .likeCount(e.getLikeCount())
                .commentCount(e.getCommentCount())
                .status(e.getStatus())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}

package com.moyue.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.account.client.UserClient;
import com.moyue.api.social.dto.BlogCommentDTO;
import com.moyue.api.social.dto.BlogPostDTO;
import com.moyue.common.core.domain.PageResult;
import com.moyue.api.account.dto.UserDTO;
import com.moyue.blog.entity.BlogCommentEntity;
import com.moyue.blog.entity.BlogLikeEntity;
import com.moyue.blog.entity.BlogPostEntity;
import com.moyue.blog.mapper.BlogCommentMapper;
import com.moyue.blog.mapper.BlogLikeMapper;
import com.moyue.blog.mapper.BlogPostMapper;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 博客空间业务：文章 / 评论 / 点赞。
 * 通过 Feign UserClient 解析 authorId / userId → 昵称；失败安全降级，不阻断主流程。
 */
@Service
public class BlogService {

    @Autowired
    private BlogPostMapper postMapper;

    @Autowired
    private BlogCommentMapper commentMapper;

    @Autowired
    private BlogLikeMapper likeMapper;

    @Autowired(required = false)
    private UserClient userClient;

    /** 新建文章，解析作者昵称 */
    public BlogPostDTO createPost(Long authorId, String title, String coverUrl,
                                  String summary, String content, Integer status) {
        BlogPostEntity e = new BlogPostEntity();
        e.setAuthorId(authorId);
        e.setTitle(title);
        e.setCoverUrl(coverUrl);
        e.setSummary(summary);
        e.setContent(content);
        e.setStatus(status == null ? 1 : status);
        e.setLikeCount(0);
        e.setCommentCount(0);
        e.setViewCount(0);
        e.setIsDeleted(0);
        postMapper.insert(e);
        return toPostDto(e);
    }

    /** 文章列表：authorId 可选（有值按作者过滤）；仅已发布(status=1)；按创建时间倒序 */
    public PageResult<BlogPostDTO> listPosts(Long authorId, int page, int size) {
        Page<BlogPostEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<BlogPostEntity> w = new LambdaQueryWrapper<BlogPostEntity>()
                .eq(BlogPostEntity::getStatus, 1)
                .orderByDesc(BlogPostEntity::getCreateTime);
        if (authorId != null) {
            w.eq(BlogPostEntity::getAuthorId, authorId);
        }
        postMapper.selectPage(p, w);

        PageResult<BlogPostDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<BlogPostDTO> records = new ArrayList<>();
        for (BlogPostEntity e : p.getRecords()) {
            records.add(toPostDto(e));
        }
        result.setRecords(records);
        return result;
    }

    /** 文章详情 + 浏览数 +1（不存在抛 RESOURCE_NOT_FOUND） */
    public BlogPostDTO getPost(Long id) {
        BlogPostEntity e = postMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setViewCount(e.getViewCount() == null ? 1 : e.getViewCount() + 1);
        postMapper.updateById(e);
        return toPostDto(e);
    }

    /** 更新文章字段（不存在抛 RESOURCE_NOT_FOUND） */
    public BlogPostDTO updatePost(Long id, String title, String coverUrl,
                                  String summary, String content, Integer status) {
        BlogPostEntity e = postMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (title != null) {
            e.setTitle(title);
        }
        if (coverUrl != null) {
            e.setCoverUrl(coverUrl);
        }
        if (summary != null) {
            e.setSummary(summary);
        }
        if (content != null) {
            e.setContent(content);
        }
        if (status != null) {
            e.setStatus(status);
        }
        postMapper.updateById(e);
        return toPostDto(e);
    }

    /** 新增评论：插入 blog_comment，并 blog_post.comment_count +1；解析评论人昵称 */
    @Transactional
    public BlogCommentDTO addComment(Long postId, Long userId, String content) {
        BlogPostEntity post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        BlogCommentEntity c = new BlogCommentEntity();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        c.setLikeCount(0);
        c.setIsDeleted(0);
        commentMapper.insert(c);

        post.setCommentCount(post.getCommentCount() == null ? 1 : post.getCommentCount() + 1);
        postMapper.updateById(post);

        return toCommentDto(c);
    }

    /** 评论列表（按创建时间升序） */
    public PageResult<BlogCommentDTO> listComments(Long postId, int page, int size) {
        Page<BlogCommentEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<BlogCommentEntity> w = new LambdaQueryWrapper<BlogCommentEntity>()
                .eq(BlogCommentEntity::getPostId, postId)
                .orderByAsc(BlogCommentEntity::getCreateTime);
        commentMapper.selectPage(p, w);

        PageResult<BlogCommentDTO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        List<BlogCommentDTO> records = new ArrayList<>();
        for (BlogCommentEntity c : p.getRecords()) {
            records.add(toCommentDto(c));
        }
        result.setRecords(records);
        return result;
    }

    /**
     * 点赞切换（@Transactional）：
     *  - 存在未删除的点赞记录 → 取消点赞（逻辑删除该记录 + like_count -1）
     *  - 不存在 → 点赞：插入 blog_like + like_count +1；若命中唯一键冲突（并发或曾取消点赞），
     *    则复活已被逻辑删除的旧记录后累加，避免重复造行。
     * 返回当前 like_count。
     */
    @Transactional
    public int toggleLike(Long postId, Long userId) {
        BlogPostEntity post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }

        BlogLikeEntity existing = likeMapper.selectOne(new LambdaQueryWrapper<BlogLikeEntity>()
                .eq(BlogLikeEntity::getPostId, postId)
                .eq(BlogLikeEntity::getUserId, userId));

        if (existing != null) {
            // 当前已点赞 → 取消点赞（全局逻辑删除：update is_deleted=1）
            likeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, post.getLikeCount() == null ? 0 : post.getLikeCount() - 1));
        } else {
            // 当前未点赞 → 点赞
            BlogLikeEntity like = new BlogLikeEntity();
            like.setPostId(postId);
            like.setUserId(userId);
            like.setIsDeleted(0);
            try {
                likeMapper.insert(like);
            } catch (DuplicateKeyException ex) {
                // 唯一键冲突：多为并发插入或曾取消点赞留下逻辑删除行 → 复活旧记录
                likeMapper.revive(postId, userId);
            }
            post.setLikeCount(post.getLikeCount() == null ? 1 : post.getLikeCount() + 1);
        }
        postMapper.updateById(post);
        return post.getLikeCount();
    }

    /** 逻辑删除文章（一并逻辑删除其评论与点赞） */
    @Transactional
    public void deletePost(Long id) {
        BlogPostEntity post = postMapper.selectById(id);
        if (post == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        // 全局逻辑删除配置下，deleteById / delete 均为 update is_deleted=1，不物理删
        commentMapper.delete(new LambdaUpdateWrapper<BlogCommentEntity>()
                .eq(BlogCommentEntity::getPostId, id));
        likeMapper.delete(new LambdaUpdateWrapper<BlogLikeEntity>()
                .eq(BlogLikeEntity::getPostId, id));
        postMapper.deleteById(id);
    }

    /** 文章实体 → DTO，并解析作者昵称（Feign 失败安全降级） */
    private BlogPostDTO toPostDto(BlogPostEntity e) {
        BlogPostDTO dto = new BlogPostDTO();
        dto.setId(e.getId());
        dto.setAuthorId(e.getAuthorId());
        dto.setAuthorName(resolveAuthorName(e.getAuthorId()));
        dto.setTitle(e.getTitle());
        dto.setCoverUrl(e.getCoverUrl());
        dto.setSummary(e.getSummary());
        dto.setContent(e.getContent());
        dto.setStatus(e.getStatus());
        dto.setLikeCount(e.getLikeCount());
        dto.setCommentCount(e.getCommentCount());
        dto.setViewCount(e.getViewCount());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    /** 评论实体 → DTO，并解析评论人昵称（Feign 失败安全降级） */
    private BlogCommentDTO toCommentDto(BlogCommentEntity c) {
        BlogCommentDTO dto = new BlogCommentDTO();
        dto.setId(c.getId());
        dto.setPostId(c.getPostId());
        dto.setUserId(c.getUserId());
        dto.setUserName(resolveUserName(c.getUserId()));
        dto.setContent(c.getContent());
        dto.setLikeCount(c.getLikeCount());
        dto.setCreateTime(c.getCreateTime());
        return dto;
    }

    private String resolveAuthorName(Long authorId) {
        if (authorId == null || userClient == null) {
            return "作者" + authorId;
        }
        try {
            UserDTO u = userClient.getUser(authorId).getData();
            if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
                return u.getNickname();
            }
        } catch (Exception ex) {
            // 用户服务未注册 / 不可用：安全降级，不阻断博客查询
        }
        return "作者" + authorId;
    }

    private String resolveUserName(Long userId) {
        if (userId == null || userClient == null) {
            return "用户" + userId;
        }
        try {
            UserDTO u = userClient.getUser(userId).getData();
            if (u != null && u.getNickname() != null && !u.getNickname().isBlank()) {
                return u.getNickname();
            }
        } catch (Exception ex) {
            // 用户服务未注册 / 不可用：安全降级，不阻断博客查询
        }
        return "用户" + userId;
    }
}

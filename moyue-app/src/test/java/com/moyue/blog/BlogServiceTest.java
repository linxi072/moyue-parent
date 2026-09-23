package com.moyue.blog;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.api.social.dto.BlogCommentDTO;
import com.moyue.api.social.dto.BlogPostDTO;
import com.moyue.blog.entity.BlogCommentEntity;
import com.moyue.blog.entity.BlogLikeEntity;
import com.moyue.blog.entity.BlogPostEntity;
import com.moyue.blog.mapper.BlogCommentMapper;
import com.moyue.blog.mapper.BlogLikeMapper;
import com.moyue.blog.mapper.BlogPostMapper;
import com.moyue.blog.service.BlogService;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BlogService 纯 Mockito 单测（不启动 Spring 容器 / 不依赖 Docker）。
 * 覆盖：文章创建字段映射与默认值、详情浏览数 +1、不存在资源 20001、
 * 评论新增与评论数累加、点赞切换与点赞数口径、以及「重复点赞只记一次 / 取消后可复活」的关键语义。
 *
 * <p>复活语义护栏：反射断言 {@link BlogLikeEntity}（及 {@link BlogPostEntity}、
 * {@link BlogCommentEntity}）的 isDeleted 字段带 {@code @TableLogic(value="0", delval="1")}。
 * 本次会话刚补齐该注解，缺失会使 blog_like 的取消点赞退化为物理删除、唯一键释放，
 * 「取消后再点赞复活旧行」语义失效——这是防回退护栏，删注解即回归。</p>
 */
class BlogServiceTest {

    /**
     * 预热 MyBatis-Plus lambda 列名缓存。
     * 纯 Mockito 单测不启动 Spring 容器，LambdaQueryWrapper/LambdaUpdateWrapper 会抛
     * "can not find lambda cache for this entity"，手动注册实体即可。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BlogPostEntity.class);
        TableInfoHelper.initTableInfo(assistant, BlogCommentEntity.class);
        TableInfoHelper.initTableInfo(assistant, BlogLikeEntity.class);
    }

    private final BlogPostMapper postMapper = mock(BlogPostMapper.class);
    private final BlogCommentMapper commentMapper = mock(BlogCommentMapper.class);
    private final BlogLikeMapper likeMapper = mock(BlogLikeMapper.class);

    private final BlogService service = createService();

    private BlogService createService() {
        BlogService s = new BlogService();
        ReflectionTestUtils.setField(s, "postMapper", postMapper);
        ReflectionTestUtils.setField(s, "commentMapper", commentMapper);
        ReflectionTestUtils.setField(s, "likeMapper", likeMapper);
        // userClient 保持 null：验证昵称安全降级（作者{id} / 用户{id}）
        return s;
    }

    private static final long AUTHOR_ID = 7L;

    private BlogPostEntity post(long id, long authorId, int likeCount, int commentCount, int viewCount) {
        BlogPostEntity e = new BlogPostEntity();
        e.setId(id);
        e.setAuthorId(authorId);
        e.setTitle("标题");
        e.setContent("正文");
        e.setStatus(1);
        e.setLikeCount(likeCount);
        e.setCommentCount(commentCount);
        e.setViewCount(viewCount);
        e.setIsDeleted(0);
        return e;
    }

    // ------------------------------ 文章创建 / 详情 ------------------------------

    @Test
    @DisplayName("创建文章：字段映射正确，status 缺省为 1，like/comment/view 均为 0，昵称降级为「作者7」")
    void createPost_mapsFieldsAndDefaults() {
        BlogPostDTO dto = service.createPost(AUTHOR_ID, "标题", "cover", "摘要", "正文", null);

        assertThat(dto.getAuthorId()).isEqualTo(AUTHOR_ID);
        assertThat(dto.getAuthorName()).isEqualTo("作者7");
        assertThat(dto.getTitle()).isEqualTo("标题");
        assertThat(dto.getCoverUrl()).isEqualTo("cover");
        assertThat(dto.getSummary()).isEqualTo("摘要");
        assertThat(dto.getContent()).isEqualTo("正文");
        assertThat(dto.getStatus()).isEqualTo(1);
        assertThat(dto.getLikeCount()).isZero();
        assertThat(dto.getCommentCount()).isZero();
        assertThat(dto.getViewCount()).isZero();
        verify(postMapper).insert(any(BlogPostEntity.class));
    }

    @Test
    @DisplayName("创建文章：显式 status=0（草稿）被如实保留")
    void createPost_explicitStatus_respected() {
        BlogPostDTO dto = service.createPost(AUTHOR_ID, "标题", null, null, "正文", 0);
        assertThat(dto.getStatus()).isZero();
    }

    @Test
    @DisplayName("文章详情：浏览数 +1 并更新")
    void getPost_incrementsViewCount() {
        BlogPostEntity e = post(1L, AUTHOR_ID, 0, 0, 5);
        when(postMapper.selectById(1L)).thenReturn(e);

        BlogPostDTO dto = service.getPost(1L);

        assertThat(dto.getViewCount()).isEqualTo(6);
        verify(postMapper).updateById(e);
    }

    @Test
    @DisplayName("文章详情：帖子不存在 → 20001")
    void getPost_missing_notFound() {
        when(postMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getPost(999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    // ------------------------------ 点赞（幂等 / 复活口径） ------------------------------

    @Test
    @DisplayName("点赞：首次点赞插入记录，like_count 0→1")
    void toggleLike_firstTime_returnsOne() {
        BlogPostEntity e = post(1L, AUTHOR_ID, 0, 0, 0);
        when(postMapper.selectById(1L)).thenReturn(e);
        when(likeMapper.selectOne(any())).thenReturn(null);

        int likeCount = service.toggleLike(1L, AUTHOR_ID);

        assertThat(likeCount).isEqualTo(1);
        verify(likeMapper).insert(any(BlogLikeEntity.class));
        verify(postMapper).updateById(e);
    }

    @Test
    @DisplayName("点赞：已点赞再次切换为取消，like_count 1→0，点赞记录逻辑删除")
    void toggleLike_alreadyLiked_cancels() {
        BlogPostEntity e = post(1L, AUTHOR_ID, 1, 0, 0);
        BlogLikeEntity like = new BlogLikeEntity();
        like.setId(50L);
        like.setPostId(1L);
        like.setUserId(AUTHOR_ID);
        when(postMapper.selectById(1L)).thenReturn(e);
        when(likeMapper.selectOne(any())).thenReturn(like);

        int likeCount = service.toggleLike(1L, AUTHOR_ID);

        assertThat(likeCount).isZero();
        verify(likeMapper).deleteById(50L);
    }

    @Test
    @DisplayName("点赞：重复点赞命中唯一键冲突 → 走复活、只记一次（不重复计数、不重复造行）")
    void toggleLike_duplicateKeyOnInsert_revivesWithoutDoubleCount() {
        BlogPostEntity e = post(1L, AUTHOR_ID, 0, 0, 0);
        when(postMapper.selectById(1L)).thenReturn(e);
        when(likeMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_post_user")).when(likeMapper).insert(any(BlogLikeEntity.class));

        int likeCount = service.toggleLike(1L, AUTHOR_ID);

        assertThat(likeCount).isEqualTo(1);
        verify(likeMapper).revive(1L, AUTHOR_ID);
        verify(likeMapper).insert(any(BlogLikeEntity.class));
    }

    @Test
    @DisplayName("点赞：允许自己给自己点赞（源码未限制）→ like_count 0→1")
    void toggleLike_selfLike_allowed() {
        BlogPostEntity e = post(1L, AUTHOR_ID, 0, 0, 0);
        when(postMapper.selectById(1L)).thenReturn(e);
        when(likeMapper.selectOne(any())).thenReturn(null);

        assertThat(service.toggleLike(1L, AUTHOR_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("点赞：帖子不存在 → 20001")
    void toggleLike_missingPost_notFound() {
        when(postMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.toggleLike(999L, AUTHOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
        verify(likeMapper, never()).insert(any(BlogLikeEntity.class));
    }

    // ------------------------------ 评论 ------------------------------

    @Test
    @DisplayName("新增博客评论：字段映射正确，评论数 +1，昵称降级为「用户7」")
    void addComment_incrementsCommentCount() {
        BlogPostEntity e = post(1L, AUTHOR_ID, 0, 0, 0);
        when(postMapper.selectById(1L)).thenReturn(e);

        BlogCommentDTO dto = service.addComment(1L, AUTHOR_ID, "很棒");

        assertThat(dto.getPostId()).isEqualTo(1L);
        assertThat(dto.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(dto.getUserName()).isEqualTo("用户7");
        assertThat(dto.getContent()).isEqualTo("很棒");
        assertThat(dto.getLikeCount()).isZero();
        assertThat(e.getCommentCount()).isEqualTo(1);
        verify(commentMapper).insert(any(BlogCommentEntity.class));
        verify(postMapper).updateById(e);
    }

    @Test
    @DisplayName("新增博客评论：帖子不存在 → 20001，不落库")
    void addComment_missingPost_notFound() {
        when(postMapper.selectById(999L)).thenReturn(null);

        assertThatCode(() -> {
            assertThatThrownBy(() -> service.addComment(999L, AUTHOR_ID, "内容"))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).getCode())
                    .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
        }).doesNotThrowAnyException();
        verify(commentMapper, never()).insert(any(BlogCommentEntity.class));
    }

    // ------------------------------ 逻辑删除护栏（防回退） ------------------------------

    @Test
    @DisplayName("逻辑删除护栏：blog 三实体的 isDeleted 必须带 @TableLogic(0/1)——缺失会使点赞取消退化为物理删除、复活语义失效")
    void blogEntities_isDeleted_annotatedWithTableLogic() throws Exception {
        for (Class<?> type : List.of(BlogPostEntity.class, BlogCommentEntity.class, BlogLikeEntity.class)) {
            Field field = type.getDeclaredField("isDeleted");
            TableLogic tableLogic = field.getAnnotation(TableLogic.class);
            assertThat(tableLogic)
                    .as("%s.isDeleted 缺少 @TableLogic：逻辑删除将退化为物理删除", type.getSimpleName())
                    .isNotNull();
            assertThat(tableLogic.value()).isEqualTo("0");
            assertThat(tableLogic.delval()).isEqualTo("1");
        }
    }
}

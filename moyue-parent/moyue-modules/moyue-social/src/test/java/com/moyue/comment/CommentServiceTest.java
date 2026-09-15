package com.moyue.comment;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.api.commerce.client.PointsClient;
import com.moyue.api.risk.client.RiskClient;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.comment.entity.CommentEntity;
import com.moyue.comment.entity.CommentLikeEntity;
import com.moyue.comment.mapper.CommentLikeMapper;
import com.moyue.comment.mapper.CommentMapper;
import com.moyue.comment.service.CommentService;
import com.moyue.common.BizException;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

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
 * CommentService 纯 Mockito 单测（不启动 Spring 容器 / 不依赖 Docker）。
 * 覆盖：发表评论字段映射与参数校验、机审 REJECT 拦截与降级放行、
 * 点赞切换与「重复点赞只记一次」（唯一键冲突走复活）、删除评论权限（本人 / 管理员）。
 *
 * <p>另含逻辑删除护栏：反射断言 {@link CommentEntity} / {@link CommentLikeEntity} 的
 * isDeleted 字段带 {@code @TableLogic(value="0", delval="1")}——本次会话刚补齐该注解，
 * 缺失会退化为物理删除，导致「逻辑删除 + 复活」语义失效。</p>
 */
class CommentServiceTest {

    /**
     * 预热 MyBatis-Plus lambda 列名缓存。
     * 纯 Mockito 单测不启动 Spring 容器，Wrappers.lambdaQuery/lambdaUpdate 会抛
     * "can not find lambda cache for this entity"，手动注册实体即可。
     */
    @BeforeAll
    static void warmUpMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CommentEntity.class);
        TableInfoHelper.initTableInfo(assistant, CommentLikeEntity.class);
    }

    private final CommentMapper commentMapper = mock(CommentMapper.class);
    private final CommentLikeMapper commentLikeMapper = mock(CommentLikeMapper.class);
    private final PointsClient pointsClient = mock(PointsClient.class);
    private final RiskClient riskClient = mock(RiskClient.class);

    private final CommentService service = createService();

    private CommentService createService() {
        CommentService s = new CommentService();
        ReflectionTestUtils.setField(s, "commentMapper", commentMapper);
        ReflectionTestUtils.setField(s, "commentLikeMapper", commentLikeMapper);
        ReflectionTestUtils.setField(s, "pointsClient", pointsClient);
        ReflectionTestUtils.setField(s, "riskClient", riskClient);
        return s;
    }

    private static final int ROLE_USER = 2;
    private static final int ROLE_ADMIN = 3;
    private static final long USER_ID = 7L;

    private CommentEntity comment(long id, int likeCount) {
        CommentEntity e = new CommentEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setBookId(10L);
        e.setLikeCount(likeCount);
        e.setIsDeleted(0);
        return e;
    }

    // ------------------------------ 发表评论 ------------------------------

    @Test
    @DisplayName("发表评论：内容 / 作品 id / 章节 id 映射正确，默认 status=0、likeCount=0、isDeleted=0，且预生成雪花 id")
    void addComment_success_mapsFieldsAndDefaults() {
        CommentEntity e = service.addComment(USER_ID, 10L, 20L, "太好看了");

        assertThat(e.getUserId()).isEqualTo(USER_ID);
        assertThat(e.getBookId()).isEqualTo(10L);
        assertThat(e.getChapterId()).isEqualTo(20L);
        assertThat(e.getContent()).isEqualTo("太好看了");
        assertThat(e.getStatus()).isZero();
        assertThat(e.getLikeCount()).isZero();
        assertThat(e.getIsDeleted()).isZero();
        assertThat(e.getId()).isNotNull();
        verify(commentMapper).insert(e);
    }

    @Test
    @DisplayName("发表评论：作品 id 为空 → 10001")
    void addComment_nullBookId_paramError() {
        assertThatThrownBy(() -> service.addComment(USER_ID, null, null, "内容"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("作品 ID 不能为空")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(commentMapper, never()).insert(any(CommentEntity.class));
    }

    @Test
    @DisplayName("发表评论：内容为空白 → 10001")
    void addComment_blankContent_paramError() {
        assertThatThrownBy(() -> service.addComment(USER_ID, 10L, null, "   "))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("评论内容不能为空")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        verify(commentMapper, never()).insert(any(CommentEntity.class));
    }

    @Test
    @DisplayName("发表评论：机审 REJECT → 抛 CONTENT_BLOCKED 20002，评论不落库")
    void addComment_riskReject_throwsAndNotInserted() {
        ModerationResultDTO result = new ModerationResultDTO();
        result.setDecision("REJECT");
        when(riskClient.moderate(any())).thenReturn(R.ok(result));

        assertThatThrownBy(() -> service.addComment(USER_ID, 10L, null, "违规内容"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.CONTENT_BLOCKED.getCode());
        verify(commentMapper, never()).insert(any(CommentEntity.class));
    }

    @Test
    @DisplayName("发表评论：机审客户端降级（返回非 0 码）→ 不阻断，评论照常落库")
    void addComment_riskDegraded_persists() {
        when(riskClient.moderate(any())).thenReturn(R.fail(40002, "risk down"));

        assertThatCode(() -> service.addComment(USER_ID, 10L, null, "正常内容"))
                .doesNotThrowAnyException();
        verify(commentMapper).insert(any(CommentEntity.class));
    }

    // ------------------------------ 点赞切换（幂等 / 只记一次） ------------------------------

    @Test
    @DisplayName("评论点赞：首次点赞插入点赞记录，like_count 0→1")
    void toggleLike_firstLike_insertsAndIncrements() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 0), comment(1L, 1));
        when(commentLikeMapper.selectOne(any())).thenReturn(null);

        int likeCount = service.toggleLike(1L, USER_ID);

        assertThat(likeCount).isEqualTo(1);
        verify(commentLikeMapper).insert(any(CommentLikeEntity.class));
        verify(commentLikeMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("评论点赞：先赞后取消净归零，取消走逻辑删除且不报错")
    void toggleLike_likeThenUnlike_netZeroNoError() {
        CommentLikeEntity like = new CommentLikeEntity();
        like.setId(50L);
        like.setCommentId(1L);
        like.setUserId(USER_ID);
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 0), comment(1L, 1), comment(1L, 1), comment(1L, 0));
        when(commentLikeMapper.selectOne(any())).thenReturn(null, like);

        assertThatCode(() -> {
            assertThat(service.toggleLike(1L, USER_ID)).isEqualTo(1);
            assertThat(service.toggleLike(1L, USER_ID)).isZero();
        }).doesNotThrowAnyException();
        verify(commentLikeMapper).deleteById(50L);
    }

    @Test
    @DisplayName("评论点赞：重复点赞命中唯一键冲突 → 走复活、只记一次（不重复造行）")
    void toggleLike_duplicateKeyOnInsert_revivesWithoutDoubleCount() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 0), comment(1L, 1));
        when(commentLikeMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("uk_comment_user"))
                .when(commentLikeMapper).insert(any(CommentLikeEntity.class));

        int likeCount = service.toggleLike(1L, USER_ID);

        assertThat(likeCount).isEqualTo(1);
        verify(commentLikeMapper).revive(1L, USER_ID);
    }

    @Test
    @DisplayName("评论点赞：评论不存在 → 20001")
    void toggleLike_missingComment_notFound() {
        when(commentMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.toggleLike(99L, USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    // ------------------------------ 删除评论（权限） ------------------------------

    @Test
    @DisplayName("删除评论：本人删除成功，走 commentMapper.deleteById（逻辑删除）")
    void deleteComment_byOwner_success() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 0));

        service.deleteComment(1L, USER_ID, ROLE_USER);

        verify(commentMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除评论：非本人且非管理员 → 10003，不删除")
    void deleteComment_byNonOwner_forbidden() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 0));

        assertThatThrownBy(() -> service.deleteComment(1L, 8L, ROLE_USER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(commentMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除评论：管理员可删除他人评论")
    void deleteComment_byAdmin_success() {
        when(commentMapper.selectById(1L)).thenReturn(comment(1L, 0));

        service.deleteComment(1L, 8L, ROLE_ADMIN);

        verify(commentMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除评论：评论不存在 → 20001")
    void deleteComment_missing_notFound() {
        when(commentMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteComment(9L, USER_ID, ROLE_USER))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    // ------------------------------ 逻辑删除护栏（防回退） ------------------------------

    @Test
    @DisplayName("逻辑删除护栏：CommentEntity / CommentLikeEntity 的 isDeleted 必须带 @TableLogic(0/1)——缺注解会退化为物理删除")
    void entities_isDeleted_annotatedWithTableLogic() throws Exception {
        assertTableLogicGuard(CommentEntity.class, "isDeleted");
        assertTableLogicGuard(CommentLikeEntity.class, "isDeleted");
    }

    private static void assertTableLogicGuard(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        assertThat(tableLogic)
                .as("%s.%s 缺少 @TableLogic：逻辑删除将退化为物理删除", type.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(tableLogic.value()).isEqualTo("0");
        assertThat(tableLogic.delval()).isEqualTo("1");
    }
}

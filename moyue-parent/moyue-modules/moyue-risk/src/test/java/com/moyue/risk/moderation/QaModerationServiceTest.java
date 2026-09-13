package com.moyue.risk.moderation;

import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import com.moyue.risk.sensitive.SensitiveWordEngine;
import com.moyue.risk.sensitive.SensitiveWordEntity;
import com.moyue.risk.sensitive.SensitiveWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModerationService 机审决策单测（P2-15）：PASS / REVIEW / REJECT 判定与 audit_task 落库。
 *
 * <p>判定规则（架构设计 §七-5）：无命中 → PASS；命中 level=2 且无 level=1 → REVIEW（写
 * audit_task 转人工，biz_type 1章节/2评论/3书籍/4用户）；出现 level=1 → REJECT。
 * 命中统计 increaseHitCount 为旁路，audit_task 写入失败不改变机审结论。</p>
 */
class QaModerationServiceTest {

    private SensitiveWordService sensitiveWordService;
    private AuditTaskMapper auditTaskMapper;
    private ModerationService moderationService;
    private SensitiveWordEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        sensitiveWordService = mock(SensitiveWordService.class);
        auditTaskMapper = mock(AuditTaskMapper.class);
        moderationService = new ModerationService();

        inject("sensitiveWordService", sensitiveWordService);
        inject("auditTaskMapper", auditTaskMapper);

        engine = new SensitiveWordEngine();
        engine.reload(List.of(
                word("诈骗", 1),
                word("广告", 2),
                word("刷单", 2)));
        when(sensitiveWordService.engine()).thenReturn(engine);
    }

    private static SensitiveWordEntity word(String word, Integer level) {
        SensitiveWordEntity e = new SensitiveWordEntity();
        e.setWord(word);
        e.setLevel(level);
        return e;
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field f = ModerationService.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(moderationService, value);
    }

    private static ModerationRequestDTO request(Integer bizType, Long bizId, String title, String content) {
        ModerationRequestDTO req = new ModerationRequestDTO();
        req.setBizType(bizType);
        req.setBizId(bizId);
        req.setTitle(title);
        req.setContent(content);
        return req;
    }

    @Test
    @DisplayName("无命中 → PASS，maxLevel=0，不写 audit_task，不累计命中")
    void shouldPassWhenNoHit() {
        ModerationResultDTO result = moderationService.moderate(request(1, 100L, "少年修仙传", "主角一路升级打怪"));

        assertThat(result.getDecision()).isEqualTo(ModerationService.DECISION_PASS);
        assertThat(result.getMaxLevel()).isZero();
        assertThat(result.getHitWords()).isEmpty();
        verify(auditTaskMapper, never()).insert(any(AuditTaskEntity.class));
        verify(sensitiveWordService, never()).increaseHitCount(anyList());
    }

    @Test
    @DisplayName("命中告警级（level=2）→ REVIEW：写 audit_task（bizType/bizId/status=0/retryCount=0）并返回 taskId，累计命中")
    void shouldReviewOnWarnLevelHit() {
        when(auditTaskMapper.insert(any(AuditTaskEntity.class))).thenAnswer(inv -> {
            AuditTaskEntity task = inv.getArgument(0);
            task.setId(99001L);
            return 1;
        });

        ModerationResultDTO result = moderationService.moderate(request(1, 200L, "工具推荐", "专业刷单团队，效果好"));

        assertThat(result.getDecision()).isEqualTo(ModerationService.DECISION_REVIEW);
        assertThat(result.getHitWords()).containsExactly("刷单");
        assertThat(result.getMaxLevel()).isEqualTo(2);
        assertThat(result.getTaskId()).isEqualTo(99001L);

        ArgumentCaptor<AuditTaskEntity> captor = ArgumentCaptor.forClass(AuditTaskEntity.class);
        verify(auditTaskMapper).insert(captor.capture());
        AuditTaskEntity task = captor.getValue();
        assertThat(task.getBizType()).isEqualTo(1);
        assertThat(task.getBizId()).isEqualTo(200L);
        assertThat(task.getStatus()).isZero();
        assertThat(task.getRetryCount()).isZero();
        assertThat(task.getRemark()).contains("刷单");
        assertThat(task.getCreateTime()).isNotNull();

        verify(sensitiveWordService).increaseHitCount(List.of("刷单"));
    }

    @Test
    @DisplayName("命中拦截级（level=1）→ REJECT：不写 audit_task，仍累计命中")
    void shouldRejectOnBlockLevelHit() {
        ModerationResultDTO result = moderationService.moderate(request(2, 300L, null, "这是诈骗信息"));

        assertThat(result.getDecision()).isEqualTo(ModerationService.DECISION_REJECT);
        assertThat(result.getHitWords()).containsExactly("诈骗");
        assertThat(result.getMaxLevel()).isEqualTo(1);
        assertThat(result.getTaskId()).isNull();
        verify(auditTaskMapper, never()).insert(any(AuditTaskEntity.class));
        verify(sensitiveWordService).increaseHitCount(List.of("诈骗"));
    }

    @Test
    @DisplayName("同时命中拦截级与告警级 → REJECT（maxLevel 取命中最高级 2），不写 audit_task")
    void shouldRejectWhenBlockAndWarnBothHit() {
        ModerationResultDTO result = moderationService.moderate(request(3, 400L, "广告与诈骗", "刷单"));

        assertThat(result.getDecision()).isEqualTo(ModerationService.DECISION_REJECT);
        assertThat(result.getMaxLevel()).isEqualTo(2);
        assertThat(result.getHitWords()).containsExactlyInAnyOrder("诈骗", "广告", "刷单");
        verify(auditTaskMapper, never()).insert(any(AuditTaskEntity.class));
    }

    @Test
    @DisplayName("audit_task 写入失败 → 机审结论仍为 REVIEW，taskId 为 null，不抛异常")
    void shouldStillReturnReviewWhenAuditTaskInsertFails() {
        when(auditTaskMapper.insert(any(AuditTaskEntity.class))).thenThrow(new RuntimeException("db down"));

        ModerationResultDTO result = moderationService.moderate(request(2, 500L, null, "加V刷单"));

        assertThat(result.getDecision()).isEqualTo(ModerationService.DECISION_REVIEW);
        assertThat(result.getTaskId()).isNull();
        assertThat(result.getHitWords()).containsExactly("刷单");
    }

    @Test
    @DisplayName("请求为 null / 标题正文全空 → PASS，不抛异常")
    void shouldPassOnNullOrEmptyRequest() {
        assertThat(moderationService.moderate(null).getDecision()).isEqualTo(ModerationService.DECISION_PASS);
        assertThat(moderationService.moderate(request(1, null, null, null)).getDecision())
                .isEqualTo(ModerationService.DECISION_PASS);
    }

    @Test
    @DisplayName("标题与正文均参与机审：标题单独命中也生效")
    void shouldMatchTitleContent() {
        ModerationResultDTO result = moderationService.moderate(request(3, 600L, "诈骗指南", "正文正常"));

        assertThat(result.getDecision()).isEqualTo(ModerationService.DECISION_REJECT);
        assertThat(result.getHitWords()).containsExactly("诈骗");
    }

    @Test
    @DisplayName("isSuccess：0 为成功码语义（与 common ResultCode 单一来源一致）")
    void isSuccessShouldUseResultCode() {
        assertThat(ModerationService.isSuccess(0)).isTrue();
        assertThat(ModerationService.isSuccess(40002)).isFalse();
    }
}

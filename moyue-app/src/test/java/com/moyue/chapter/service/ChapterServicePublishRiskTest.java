package com.moyue.chapter.service;

import com.moyue.api.risk.client.BehaviorRiskClient;
import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.mapper.ChapterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChapterService 发布行为风控埋点单测（Mockito 隔离 Mapper / 风控客户端，闭环 P2-C 收尾）：
 * 验证「即时发布采集 PUBLISH」「定时发布（status=4）不采集」「风控客户端未注册不阻断」。
 * 写法与 {@code AiServiceChatIndexHookTest} 对齐（ReflectionTestUtils 注入 mock，不启 Spring）。
 * 以管理员角色（role=3）调用 publish 跳过归属校验，聚焦 PUBLISH 埋点契约。
 */
class ChapterServicePublishRiskTest {

    private final ChapterMapper chapterMapper = mock(ChapterMapper.class);
    private final BehaviorRiskClient behaviorRiskClient = mock(BehaviorRiskClient.class);

    private ChapterService createService(boolean withClient) {
        ChapterService s = new ChapterService();
        ReflectionTestUtils.setField(s, "chapterMapper", chapterMapper);
        if (withClient) {
            ReflectionTestUtils.setField(s, "behaviorRiskClient", behaviorRiskClient);
        }
        return s;
    }

    private ChapterEntity chapter(Long id, Long bookId) {
        ChapterEntity e = new ChapterEntity();
        e.setId(id);
        e.setBookId(bookId);
        e.setTitle("标题");
        e.setContent("正文内容");
        e.setStatus(0);
        return e;
    }

    @Test
    @DisplayName("publish 即时发布（status=2）：采集 PUBLISH 事件，行为主体为发布人")
    void publish_immediate_collectsPublish() {
        ChapterService service = createService(true);
        when(chapterMapper.selectById(any())).thenReturn(chapter(8001L, 123L));
        when(chapterMapper.updateById(any(ChapterEntity.class))).thenReturn(1);

        service.publish(8001L, 7001L, 3, null);

        verify(behaviorRiskClient, times(1)).collect(eq(7001L), any(), eq("PUBLISH"), eq(8001L), any());
    }

    @Test
    @DisplayName("publish 定时发布（status=4）：未实际发布，不采集 PUBLISH")
    void publish_scheduled_doesNotCollect() {
        ChapterService service = createService(true);
        when(chapterMapper.selectById(any())).thenReturn(chapter(8001L, 123L));
        when(chapterMapper.updateById(any(ChapterEntity.class))).thenReturn(1);

        service.publish(8001L, 7001L, 3, LocalDateTime.now().plusDays(1));

        verify(behaviorRiskClient, never()).collect(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("behaviorRiskClient 未注册：publish 正常返回，不抛 NPE")
    void publish_riskClientNull_safe() {
        ChapterService service = createService(false);
        when(chapterMapper.selectById(any())).thenReturn(chapter(8001L, 123L));
        when(chapterMapper.updateById(any(ChapterEntity.class))).thenReturn(1);

        ChapterEntity result = service.publish(8001L, 7001L, 3, null);

        assertThat(result.getStatus()).isEqualTo(2);
        verify(behaviorRiskClient, never()).collect(any(), any(), any(), any(), any());
    }
}

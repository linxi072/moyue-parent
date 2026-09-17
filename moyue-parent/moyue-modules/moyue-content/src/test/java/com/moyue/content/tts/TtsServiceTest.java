package com.moyue.content.tts;

import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.content.tts.dto.TtsChapterDTO;
import com.moyue.content.tts.dto.TtsSegmentDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TtsService 业务测试（Mockito，无 Spring 上下文）：覆盖成功合成、TTS 降级、章节未发布、正文为空、语速越界。
 */
@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock
    private ChapterService chapterService;
    @Mock
    private TtsProvider ttsProvider;

    @InjectMocks
    private TtsService ttsService;

    @BeforeEach
    void setUp() {
        ttsService.setSegmentMaxChars(200);
        ttsService.setDefaultVoice("");
    }

    private ChapterEntity published(String content) {
        ChapterEntity c = new ChapterEntity();
        c.setId(1L);
        c.setStatus(2);
        c.setContent(content);
        return c;
    }

    @Test
    void success_eachSegmentHasBase64() {
        when(chapterService.getById(1L)).thenReturn(published("第一段。第二段。"));
        when(ttsProvider.synthesize(anyString(), any(), anyDouble())).thenReturn("audio-bytes".getBytes());

        TtsChapterDTO dto = ttsService.synthesizeChapter(1L, null, null);

        assertThat(dto.getChapterId()).isEqualTo(1L);
        assertThat(dto.getSegments()).hasSize(2);
        for (TtsSegmentDTO seg : dto.getSegments()) {
            assertThat(seg.getAudioBase64()).isNotBlank();
        }
        assertThat(dto.getTotalDurationSec()).isGreaterThan(0);
    }

    @Test
    void ttsUnavailable_degradesToServiceDegraded() {
        when(chapterService.getById(1L)).thenReturn(published("第一段。"));
        when(ttsProvider.synthesize(anyString(), any(), anyDouble())).thenThrow(new TtsUnavailableException("down"));

        BizException ex = catchThrowableOfType(() -> ttsService.synthesizeChapter(1L, null, 1.0), BizException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.SERVICE_DEGRADED.getCode());
    }

    @Test
    void unpublishedChapter_resourceNotFound() {
        ChapterEntity c = published("x");
        c.setStatus(1);
        when(chapterService.getById(1L)).thenReturn(c);

        BizException ex = catchThrowableOfType(() -> ttsService.synthesizeChapter(1L, null, 1.0), BizException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void emptyContent_paramError() {
        when(chapterService.getById(1L)).thenReturn(published(""));

        BizException ex = catchThrowableOfType(() -> ttsService.synthesizeChapter(1L, null, 1.0), BizException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    void invalidSpeed_paramError() {
        when(chapterService.getById(1L)).thenReturn(published("一段。"));

        BizException ex = catchThrowableOfType(() -> ttsService.synthesizeChapter(1L, null, 3.0), BizException.class);
        assertThat(ex.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }
}

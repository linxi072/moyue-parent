package com.moyue.content.tts;

import com.moyue.chapter.entity.ChapterEntity;
import com.moyue.chapter.service.ChapterService;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.content.tts.dto.TtsChapterDTO;
import com.moyue.content.tts.dto.TtsSegmentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.List;

/**
 * 智能朗读业务：取已发布章节正文 → 分段 → 逐段 TTS 合成 → 组装音频片段。
 *
 * <p>降级契约：TTS 不可用（缺密钥/调用失败/返回空）时抛 {@link BizException}
 * {@code SERVICE_DEGRADED(40002)}，HTTP 非 5xx，阅读域其余接口不受影响。</p>
 *
 * <p>仅后端：音频以 Base64 内联（{@code TtsSegmentDTO.audioBase64}）返回，无对象存储依赖；
 * 前端播放器由后续 P2-F 立项。</p>
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    /** 章节状态：已发布（与 ChapterService.STATUS_PUBLISHED 对齐） */
    private static final int STATUS_PUBLISHED = 2;

    /** 中文朗读语速基准：约 5 字/秒 */
    private static final double CHARS_PER_SEC = 5.0;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private TtsProvider ttsProvider;

    @Value("${moyue.tts.segment-max-chars:200}")
    private int segmentMaxChars;

    @Value("${moyue.tts.default-voice:}")
    private String defaultVoice;

    /** 单元测试可覆写分段阈值与默认音色（不依赖 @Value 注入） */
    public void setSegmentMaxChars(int segmentMaxChars) {
        this.segmentMaxChars = segmentMaxChars;
    }

    public void setDefaultVoice(String defaultVoice) {
        this.defaultVoice = defaultVoice;
    }

    /**
     * 合成整章朗读音频。
     *
     * @param chapterId 章节 ID
     * @param voice     音色（可空 → 默认音色）
     * @param speed     语速（可空 → 1.0，范围 0.5~2.0）
     * @return 章节朗读结果（有序片段 + 预计时长）
     */
    public TtsChapterDTO synthesizeChapter(Long chapterId, String voice, Double speed) {
        if (chapterId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节 ID 不能为空");
        }
        ChapterEntity chapter = chapterService.getById(chapterId);
        if (chapter == null || !Integer.valueOf(STATUS_PUBLISHED).equals(chapter.getStatus())) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "章节不存在或未发布");
        }
        String content = chapter.getContent();
        if (!StringUtils.hasText(content)) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节正文为空，无法朗读");
        }
        double sp = (speed == null) ? 1.0 : speed;
        if (sp < 0.5 || sp > 2.0) {
            throw new BizException(ResultCode.PARAM_ERROR, "语速需在 0.5~2.0 之间");
        }
        String v = StringUtils.hasText(voice) ? voice : defaultVoice;

        List<TtsSegmentDTO> segments = ChapterSplitter.split(content, segmentMaxChars);
        for (TtsSegmentDTO seg : segments) {
            try {
                byte[] audio = ttsProvider.synthesize(seg.getText(), v, sp);
                if (audio == null || audio.length == 0) {
                    throw new TtsUnavailableException("TTS 返回空音频");
                }
                seg.setAudioBase64(Base64.getEncoder().encodeToString(audio));
            } catch (TtsUnavailableException ex) {
                log.warn("[tts] 章节 {} 合成降级：{}", chapterId, ex.getMessage());
                throw new BizException(ResultCode.SERVICE_DEGRADED, "朗读服务暂不可用，请稍后重试");
            }
        }

        TtsChapterDTO dto = new TtsChapterDTO();
        dto.setChapterId(chapterId);
        dto.setSegments(segments);
        dto.setTotalDurationSec((int) Math.ceil(content.length() / (CHARS_PER_SEC * sp)));
        return dto;
    }
}

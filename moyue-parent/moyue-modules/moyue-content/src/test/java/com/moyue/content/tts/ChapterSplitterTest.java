package com.moyue.content.tts;

import com.moyue.content.tts.dto.TtsSegmentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 长文分段器纯逻辑测试（无 Spring 上下文）。
 */
class ChapterSplitterTest {

    @Test
    void emptyText_returnsEmpty() {
        assertThat(ChapterSplitter.split("", 200)).isEmpty();
        assertThat(ChapterSplitter.split(null, 200)).isEmpty();
    }

    @Test
    void shortText_singleSegment() {
        String text = "今天天气真好。";
        List<TtsSegmentDTO> segs = ChapterSplitter.split(text, 200);
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getIndex()).isZero();
        assertThat(segs.get(0).getText()).isEqualTo(text);
        assertThat(segs.get(0).getCharOffsetStart()).isZero();
        assertThat(segs.get(0).getCharOffsetEnd()).isEqualTo(text.length());
    }

    @Test
    void punctuationAnchoredSplit_concatRestoresOriginal() {
        String text = "第一句话。第二句话！第三句话？";
        List<TtsSegmentDTO> segs = ChapterSplitter.split(text, 200);
        assertThat(segs).hasSize(3);
        StringBuilder sb = new StringBuilder();
        int expectedOffset = 0;
        for (TtsSegmentDTO s : segs) {
            assertThat(s.getCharOffsetStart()).isEqualTo(expectedOffset);
            sb.append(s.getText());
            expectedOffset = s.getCharOffsetEnd();
        }
        assertThat(sb.toString()).isEqualTo(text);
    }

    @Test
    void longSingleSentence_hardSplit_neverExceedMax() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append("字");
        String text = sb.toString() + "。";
        List<TtsSegmentDTO> segs = ChapterSplitter.split(text, 200);
        assertThat(segs.size()).isGreaterThan(1);
        for (TtsSegmentDTO s : segs) {
            assertThat(s.getText().length()).isLessThanOrEqualTo(200);
        }
        assertThat(segs.get(segs.size() - 1).getText()).endsWith("。");
    }

    @Test
    void noAnchorText_neverExceedMaxChars() {
        String text = "abcd".repeat(300); // 1200 chars, 无锚点
        List<TtsSegmentDTO> segs = ChapterSplitter.split(text, 200);
        for (TtsSegmentDTO s : segs) {
            assertThat(s.getText().length()).isLessThanOrEqualTo(200);
        }
    }

    @Test
    void offsetsContinuousAndCovering() {
        String text = "甲。乙。丙。丁。";
        List<TtsSegmentDTO> segs = ChapterSplitter.split(text, 200);
        int off = 0;
        for (TtsSegmentDTO s : segs) {
            assertThat(s.getCharOffsetStart()).isEqualTo(off);
            off = s.getCharOffsetEnd();
        }
        assertThat(off).isEqualTo(text.length());
    }
}

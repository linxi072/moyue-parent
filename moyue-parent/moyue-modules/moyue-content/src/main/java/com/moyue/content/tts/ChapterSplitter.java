package com.moyue.content.tts;

import com.moyue.content.tts.dto.TtsSegmentDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 长文分段器：将章节正文切分为适合 TTS 单请求的片段。
 *
 * <p>切分锚点（中文语义边界）：句号、感叹号、问号、换行（含中英文符号）。
 * 单段上限 {@code maxChars}（默认 200，可配 {@code moyue.tts.segment-max-chars}），
 * 超过上限的「超长单句」按字符硬切（保证每段 ≤ 上限，不切断已分句的语义单元）。
 * 每个片段记录在原文本中的字符偏移，供前端断点续听对齐。</p>
 */
public final class ChapterSplitter {

    /** 默认单段字符上限 */
    public static final int DEFAULT_MAX_CHARS = 200;

    private static final Pattern HAS_ANCHOR = Pattern.compile("[。！？!?\\n]");

    private ChapterSplitter() {
    }

    /**
     * 分段。
     *
     * <p>规则：先按标点为锚点（句号/感叹号/问号/换行，含中英文符号）切句，每句独立成一段；
     * 仅当「单句」超过 {@code maxChars} 时，按字符硬切为多段（不切断可视语义，但受 TTS 单请求上限约束）。
     * 句级分段使 {@code segment.index} 与书架 {@code listen_segment_index} 续播对齐。</p>
     *
     * @param text      章节正文（可空）
     * @param maxChars  单段上限（≤0 时使用 {@link #DEFAULT_MAX_CHARS}）
     * @return 有序片段列表（含 index / text / 字符偏移）；空文本返回空列表
     */
    public static List<TtsSegmentDTO> split(String text, int maxChars) {
        List<TtsSegmentDTO> segs = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segs;
        }
        if (maxChars <= 0) {
            maxChars = DEFAULT_MAX_CHARS;
        }
        int offset = 0;
        int idx = 0;

        // 按锚点切句（lookbehind 保留锚点字符在句尾）
        String[] sentences = text.split("(?<=[。！？!?\\n])");
        for (String s : sentences) {
            if (s.isEmpty()) {
                continue;
            }
            // 超长单句：按字符硬切为多段
            if (s.length() > maxChars) {
                int i = 0;
                while (i < s.length()) {
                    int end = Math.min(i + maxChars, s.length());
                    String chunk = s.substring(i, end);
                    segs.add(make(chunk, idx++, offset));
                    offset += chunk.length();
                    i = end;
                }
                continue;
            }
            segs.add(make(s, idx++, offset));
            offset += s.length();
        }
        return segs;
    }

    private static TtsSegmentDTO make(String text, int index, int offset) {
        TtsSegmentDTO d = new TtsSegmentDTO();
        d.setIndex(index);
        d.setText(text);
        d.setCharOffsetStart(offset);
        d.setCharOffsetEnd(offset + text.length());
        return d;
    }

    /** 文本是否含有可切分锚点（用于测试/诊断） */
    public static boolean hasAnchor(String text) {
        return text != null && HAS_ANCHOR.matcher(text).find();
    }
}

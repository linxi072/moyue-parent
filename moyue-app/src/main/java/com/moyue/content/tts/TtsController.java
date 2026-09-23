package com.moyue.content.tts;

import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.content.tts.dto.TtsChapterDTO;
import com.moyue.read.dto.ListenProgressDTO;
import com.moyue.read.service.ReadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能朗读接口（读者端听书，仅后端）。
 *
 * <p>复用阅读域网关路由 {@code /api/v1/read/**}（moyue-read），不新增 gateway route。</p>
 * <p>userId 一律取自网关注入的 {@code X-User-Id}（{@link Constants#USER_ID_HEADER}），不接受前端传入。</p>
 */
@RestController
@RequestMapping("/api/v1/read")
public class TtsController {

    @Autowired
    private TtsService ttsService;

    @Autowired
    private ReadService readService;

    /**
     * 合成整章朗读音频（Base64 内联，P0 默认形态）。
     * TTS 不可用时返回 {@code SERVICE_DEGRADED(40002)}，HTTP 非 5xx，阅读域其余接口不受影响。
     */
    @PostMapping("/chapters/{chapterId}/tts")
    public R<TtsChapterDTO> synthesize(@PathVariable Long chapterId,
                                        @RequestParam(required = false) String voice,
                                        @RequestParam(required = false, defaultValue = "1.0") Double speed) {
        return R.ok(ttsService.synthesizeChapter(chapterId, voice, speed));
    }

    /** 保存听书进度（断点续听，P2-L） */
    @PutMapping("/bookshelf/{bookId}/listen-progress")
    public R<Void> saveListenProgress(@PathVariable Long bookId,
                                      @RequestBody ListenProgressRequest req,
                                      HttpServletRequest request) {
        readService.saveListenProgress(requireUserId(request), bookId,
                req.getChapterId(), req.getSegmentIndex(), req.getCharOffset());
        return R.ok();
    }

    /** 读取听书进度（续播定位，P2-L） */
    @GetMapping("/bookshelf/{bookId}/listen-progress")
    public R<ListenProgressDTO> getListenProgress(@PathVariable Long bookId, HttpServletRequest request) {
        return R.ok(readService.getListenProgress(requireUserId(request), bookId));
    }

    // ------------------------------ 上下文工具 ------------------------------

    private Long requireUserId(HttpServletRequest request) {
        String uid = request.getHeader(Constants.USER_ID_HEADER);
        if (uid == null || uid.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(uid.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    /** 听书进度保存请求 */
    @Data
    public static class ListenProgressRequest {
        private Long chapterId;
        private Integer segmentIndex;
        private Integer charOffset;
    }
}

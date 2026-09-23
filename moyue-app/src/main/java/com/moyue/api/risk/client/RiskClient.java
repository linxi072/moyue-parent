package com.moyue.api.risk.client;

import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.risk.moderation.ModerationService;
import org.springframework.stereotype.Component;

/**
 * 内容安全服务进程内适配器（monolith 版）。
 * 原 @FeignClient(moyue-risk) 已移除 OpenFeign，改为直接注入 {@link ModerationService} 委托调用。
 */
@Component
public class RiskClient {

    private final ModerationService moderationService;

    public RiskClient(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /** 提交机审：命中敏感词 → PASS / REVIEW / REJECT */
    public R<ModerationResultDTO> moderate(ModerationRequestDTO request) {
        try {
            return R.ok(moderationService.moderate(request));
        } catch (Exception e) {
            return R.fail(ResultCode.SERVICE_DEGRADED);
        }
    }
}

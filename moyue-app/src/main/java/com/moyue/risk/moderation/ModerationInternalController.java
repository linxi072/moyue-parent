package com.moyue.risk.moderation;

import com.moyue.api.risk.dto.ModerationRequestDTO;
import com.moyue.api.risk.dto.ModerationResultDTO;
import com.moyue.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 机审内部端点：实现 moyue-api-risk 的 RiskClient 契约。
 * <p>POST /api/v1/internal/risk/moderate —— 路径 /api/v1/internal/** 不在网关任何路由内，
 * 仅限服务间（moyue-content / moyue-social 经 Feign）调用。</p>
 */
@RestController
@RequestMapping("/api/v1/internal/risk")
public class ModerationInternalController {

    @Autowired
    private ModerationService moderationService;

    /** 机审：命中敏感词 → PASS / REVIEW / REJECT */
    @PostMapping("/moderate")
    public R<ModerationResultDTO> moderate(@RequestBody ModerationRequestDTO request) {
        return R.ok(moderationService.moderate(request));
    }
}

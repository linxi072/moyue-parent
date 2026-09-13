package com.moyue.api.client;

import com.moyue.api.dto.ModerationRequestDTO;
import com.moyue.api.dto.ModerationResultDTO;
import com.moyue.common.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 内容安全服务 Feign 客户端（moyue-risk）。
 * 返回类型包裹 R&lt;T&gt;，与 ModerationInternalController 内部端点结构一致。
 * 供 moyue-content（章节机审）、moyue-social（评论机审）在内容落库前调用（P2-15）。
 */
@FeignClient(name = "moyue-admin")
public interface RiskClient {

    /** 提交机审：命中敏感词 → PASS / REVIEW / REJECT */
    @PostMapping("/api/v1/internal/risk/moderate")
    R<ModerationResultDTO> moderate(@RequestBody ModerationRequestDTO request);
}

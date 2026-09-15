package com.moyue.operation.controller;

import com.moyue.api.system.dto.SettlementDTO;
import com.moyue.common.BizException;
import com.moyue.common.Constants;
import com.moyue.common.R;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.SettlementOrderEntity;
import com.moyue.operation.service.SettlementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 稿酬结算单接口。
 * 作者端：/api/v1/settlements（发起 / 我的列表）；
 * 管理端：/api/v1/admin/settlements/**（审核 / 打款 / 列表，受 AdminRoleInterceptor 保护，仅 role=3）。
 */
@RestController
@RequestMapping("/api/v1")
public class SettlementController {

    @Autowired
    private SettlementService settlementService;

    /** 作者发起结算单：POST /api/v1/settlements（authorId 取网关 X-User-Id） */
    @PostMapping("/settlements")
    public R<SettlementOrderEntity> create(@RequestBody CreateSettlementRequest req, HttpServletRequest request) {
        long authorId = requireUserId(request);
        return R.ok(settlementService.createSettlement(authorId, req.getPeriod()));
    }

    /** 我的结算单：GET /api/v1/settlements/mine */
    @GetMapping("/settlements/mine")
    public R<List<SettlementOrderEntity>> mine(HttpServletRequest request) {
        long authorId = requireUserId(request);
        return R.ok(settlementService.listByAuthor(authorId));
    }

    /** 管理端审核通过：POST /api/v1/admin/settlements/{id}/audit（operatorId 取 X-User-Id） */
    @PostMapping("/admin/settlements/{id}/audit")
    public R<SettlementOrderEntity> audit(@PathVariable Long id, HttpServletRequest request) {
        long operatorId = requireUserId(request);
        return R.ok(settlementService.auditApprove(id, operatorId));
    }

    /** 管理端打款：POST /api/v1/admin/settlements/{id}/payout */
    @PostMapping("/admin/settlements/{id}/payout")
    public R<SettlementOrderEntity> payout(@PathVariable Long id) {
        return R.ok(settlementService.payout(id));
    }

    /** 管理端结算单列表：GET /api/v1/admin/settlements */
    @GetMapping("/admin/settlements")
    public R<List<SettlementOrderEntity>> listAll() {
        return R.ok(settlementService.listAll());
    }

    // ------------------------------ 内部端点（服务间 Feign，不经网关） ------------------------------

    /**
     * 内部端点：按作者查询结算单，供 moyue-commerce 经 {@code SettlementClient} 调用。
     * GET /api/v1/internal/settlements?authorId=
     */
    @GetMapping("/internal/settlements")
    public R<List<SettlementDTO>> internalListByAuthor(@RequestParam("authorId") Long authorId) {
        return R.ok(settlementService.listByAuthor(authorId).stream().map(SettlementController::toDto).toList());
    }

    /**
     * 内部端点：按 ID 查询单张结算单。
     * GET /api/v1/internal/settlements/{id}
     */
    @GetMapping("/internal/settlements/{id}")
    public R<SettlementDTO> internalGetById(@PathVariable Long id) {
        return R.ok(toDto(settlementService.getById(id)));
    }

    /** 实体 → 跨服务契约 DTO */
    private static SettlementDTO toDto(SettlementOrderEntity e) {
        SettlementDTO dto = new SettlementDTO();
        dto.setId(e.getId());
        dto.setAuthorId(e.getAuthorId());
        dto.setPeriod(e.getPeriod());
        dto.setTotalAmount(e.getTotalAmount());
        dto.setStatus(e.getStatus());
        dto.setPayChannel(resolvePayChannel(e.getPayChannel()));
        dto.setPaySerial(e.getPaySerial());
        dto.setRemark(e.getRemark());
        dto.setOperatorId(e.getOperatorId());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    /** 持久化渠道枚举（1 微信 / 2 支付宝）→ 契约层渠道标识 */
    private static String resolvePayChannel(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 1 -> "wechat";
            case 2 -> "alipay";
            default -> "stub";
        };
    }

    private long requireUserId(HttpServletRequest request) {
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

    @Data
    public static class CreateSettlementRequest {
        /** 结算月份 YYYY-MM（本期要求必传） */
        private String period;
    }
}

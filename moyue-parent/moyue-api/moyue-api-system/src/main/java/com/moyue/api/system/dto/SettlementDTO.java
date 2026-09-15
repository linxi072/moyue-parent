package com.moyue.api.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 结算单 DTO（跨服务契约）。
 * 由 moyue-system 的结算单实体映射而来，经 {@code SettlementClient} 提供给 moyue-commerce 展示。
 * 保持纯 POJO，不依赖任何实现模块，避免 api 契约层反向依赖业务实现。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SettlementDTO {

    /** 结算单主键（雪花） */
    private Long id;

    /** 作者 ID → user.id */
    private Long authorId;

    /** 结算周期（YYYY-MM） */
    private String period;

    /** 结算总金额（元） */
    private BigDecimal totalAmount;

    /** 状态：0 待结算 / 1 已结算待打款 / 2 已打款 / 3 打款失败 */
    private Integer status;

    /** 支付渠道标识（wechat_pay / alipay / stub） */
    private String payChannel;

    /** 渠道流水号（打款幂等键） */
    private String paySerial;

    /** 备注（审核 / 打款说明） */
    private String remark;

    /** 操作人（审核 / 打款用户 ID） */
    private Long operatorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 状态码 → 文案映射（与 moyue-system SettlementStateMachine 保持一致） */
    private static final Map<Integer, String> STATUS_DESC = Map.of(
            0, "待结算",
            1, "已结算待打款",
            2, "已打款",
            3, "打款失败");

    /** 状态文案（仅出参，不参与反序列化） */
    public String getStatusDesc() {
        return STATUS_DESC.getOrDefault(status, "未知");
    }
}

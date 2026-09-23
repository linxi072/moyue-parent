package com.moyue.api.system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作者稿酬流水跨服务契约 DTO（P2-H）。
 * 收敛「commerce 双映射 author_income 同表」技术债：该表唯一写方为 moyue-system，
 * commerce 经 {@code IncomeClient} 查询，不再直读表。字段与 author_income 一一对应。
 */
@Data
public class AuthorIncomeDTO {

    /** 流水主键（雪花 ID） */
    private Long id;

    /** 作者 ID → user.id */
    private Long authorId;

    /** 作品 ID → book.id */
    private Long bookId;

    /** 来源订单号（打赏分成） */
    private String orderNo;

    /** 类型：1 订阅 / 2 打赏分成 / 3 全勤奖 / 4 买断分成 */
    private Integer incomeType;

    /** 金额（元） */
    private BigDecimal amount;

    /** 结算月份 YYYY-MM */
    private String settleMonth;

    /** 关联结算单 ID（V16 新增，由 system 回写） */
    private Long settlementId;

    /** 创建时间 */
    private LocalDateTime createTime;
}

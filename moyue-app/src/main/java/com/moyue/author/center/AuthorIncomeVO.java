package com.moyue.author.center;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作者稿酬流水 VO（投影 {@code AuthorIncomeDTO}）。
 * 供 {@code GET /api/v1/author/center/income} 返回；可按 bookId 过滤。
 */
@Data
public class AuthorIncomeVO {

    /** 流水主键 */
    private Long id;

    /** 作者 ID */
    private Long authorId;

    /** 作品 ID */
    private Long bookId;

    /** 类型：1 订阅 / 2 打赏分成 / 3 全勤奖 / 4 买断分成 */
    private Integer incomeType;

    /** 金额（元） */
    private BigDecimal amount;

    /** 结算月份 YYYY-MM */
    private String settleMonth;

    /** 关联结算单 ID */
    private Long settlementId;

    /** 创建时间 */
    private LocalDateTime createTime;
}

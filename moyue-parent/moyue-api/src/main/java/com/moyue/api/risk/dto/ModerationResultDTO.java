package com.moyue.api.risk.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 机审结果 DTO（跨服务共享）。
 * 判定规则（架构设计 §七-5）：无命中 → {@code PASS}；有 {@code level=2} 命中且无 {@code level=1}
 * → {@code REVIEW}（转人工）；出现 {@code level=1} 命中 → {@code REJECT}。{@code maxLevel} 取命中最高级。
 */
@Data
public class ModerationResultDTO implements Serializable {

    /** 处置结论：PASS | REVIEW | REJECT */
    private String decision;

    /** 命中的敏感词列表 */
    private List<String> hitWords;

    /** 命中最高等级：0 无 / 1 拦截 / 2 告警 */
    private Integer maxLevel;

    /** REVIEW 时生成的 audit_task.id，可为空 */
    private Long taskId;
}

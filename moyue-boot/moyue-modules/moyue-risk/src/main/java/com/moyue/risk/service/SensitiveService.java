package com.moyue.risk.service;

import java.util.Optional;

/**
 * 敏感词检测服务（风控域）。
 *
 * <p>供 content / social 等模块在上架、发评论、发帖时调用，判断文本是否命中敏感词，
 * 并返回命中的最高级别（1 拦截 / 2 告警）。命中即累加 {@code hit_count}（异步统计，
 * 此处同步更新以保证计数准确）。真实敏感词库为结构占位，仅做本地 MySQL 词表匹配。</p>
 *
 * @author moyue
 */
public interface SensitiveService {

    /** 文本是否命中任一启用的敏感词 */
    boolean contains(String text);

    /**
     * 返回命中的最高级别：1 拦截 / 2 告警；未命中返回 {@link Optional#empty()}。
     */
    Optional<Integer> matchLevel(String text);

    /**
     * 返回命中的首个敏感词原文（用于回显），未命中返回 {@link Optional#empty()}。
     */
    Optional<String> firstHit(String text);
}

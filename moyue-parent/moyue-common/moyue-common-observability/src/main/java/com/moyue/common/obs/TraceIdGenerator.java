package com.moyue.common.obs;

import java.util.UUID;

/**
 * TraceId 生成工具：产出 32 位十六进制（UUID 去横线），全局唯一、可读。
 */
public final class TraceIdGenerator {

    private TraceIdGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

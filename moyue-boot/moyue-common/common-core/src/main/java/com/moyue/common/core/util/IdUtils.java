package com.moyue.common.core.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 全局 ID 生成器：统一雪花 ID（架构说明书 6.5：主键统一雪花 ID，不暴露业务量）。
 *
 * <p>【待确认】workerId / datacenterId 在容器环境下建议由环境变量或 Nacos 下发，
 * 当前默认取 1，多实例部署需显式配置，否则存在 ID 冲突风险。
 *
 * @author moyue
 */
public final class IdUtils {

    private static final Snowflake SNOWFLAKE =
            IdUtil.getSnowflake(Long.getLong("moyue.snowflake.workerId", 1L),
                                Long.getLong("moyue.snowflake.datacenterId", 1L));

    private IdUtils() {
    }

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }
}

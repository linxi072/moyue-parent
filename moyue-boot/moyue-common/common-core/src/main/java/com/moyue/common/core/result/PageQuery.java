package com.moyue.common.core.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询入参：page / size（架构说明书 11.2）。
 *
 * <p>刻意不引入 OpenAPI 注解，保持 common-core 轻量；接口文档注解加在各模块的 DTO 上。
 *
 * @author moyue
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码最小为 1")
    private long page = 1;

    /** 每页条数，最大 200 */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 200, message = "每页条数最大为 200")
    private long size = 10;
}

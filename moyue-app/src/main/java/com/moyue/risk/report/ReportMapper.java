package com.moyue.risk.report;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报 Mapper。
 * <p>由 RiskApplication 的 {@code @MapperScan("com.moyue")} 自动扫描注册；
 * 逻辑删除为业务侧维护（update is_deleted=1），不使用物理 delete。</p>
 */
@Mapper
public interface ReportMapper extends BaseMapper<ReportEntity> {
}

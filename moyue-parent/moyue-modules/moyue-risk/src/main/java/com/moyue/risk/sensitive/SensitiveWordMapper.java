package com.moyue.risk.sensitive;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper。
 * <p>由 RiskApplication 的 {@code @MapperScan("com.moyue")} 自动扫描注册；
 * 逻辑删除为业务侧维护（update is_deleted=1），不使用物理 delete。</p>
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWordEntity> {
}

package com.moyue.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.domain.entity.AuditRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核工单数据访问。
 *
 * @author moyue
 */
@Mapper
public interface AuditRecordMapper extends BaseMapper<AuditRecord> {
}

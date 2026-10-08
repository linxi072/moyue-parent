package com.moyue.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyue.risk.domain.entity.ReportTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报工单 Mapper（风控域）。
 *
 * @author moyue
 */
@Mapper
public interface ReportTicketMapper extends BaseMapper<ReportTicket> {
}

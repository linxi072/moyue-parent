package com.moyue.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.risk.domain.dto.query.ReportTicketQuery;
import com.moyue.risk.domain.entity.ReportTicket;
import com.moyue.risk.domain.vo.ReportTicketVO;
import com.moyue.risk.mapper.ReportTicketMapper;
import com.moyue.risk.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 举报工单实现：提交 / 列表 / 处理（状态流转 0 待处理 → 1 已处理 / 2 驳回）。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportTicketMapper reportMapper;

    @Override
    public PageResult<ReportTicketVO> pageReports(ReportTicketQuery query) {
        var page = PageUtils.<ReportTicket>page(query);
        var result = reportMapper.selectPage(page, new LambdaQueryWrapper<ReportTicket>()
                .eq(query.getBizType() != null, ReportTicket::getBizType, query.getBizType())
                .eq(query.getStatus() != null, ReportTicket::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getBizId()), ReportTicket::getBizId, query.getBizId())
                .eq(query.getReporterId() != null, ReportTicket::getReporterId, query.getReporterId())
                .orderByDesc(ReportTicket::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(ReportTicket entity) {
        if (entity.getBizType() == null) {
            entity.setBizType(ReportTicket.BIZ_BOOK);
        }
        if (entity.getReporterId() == null) {
            entity.setReporterId(0L);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(ReportTicket.STATUS_PENDING);
        }
        reportMapper.insert(entity);
        log.info("收到举报：bizType={} bizId={} reporter={}", entity.getBizType(), entity.getBizId(), entity.getReporterId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handle(Long id, Integer status, String handler, String handleReason) {
        if (status == null || (status != ReportTicket.STATUS_HANDLED && status != ReportTicket.STATUS_REJECTED)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "处理状态不合法（1 已处理 / 2 驳回）");
        }
        ReportTicket exist = reportMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("举报工单");
        }
        if (exist.getStatus() != null && exist.getStatus() != ReportTicket.STATUS_PENDING) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工单已处理，不能重复处理");
        }
        return reportMapper.update(null, new LambdaUpdateWrapper<ReportTicket>()
                .eq(ReportTicket::getId, id)
                .set(ReportTicket::getStatus, status)
                .set(StringUtils.hasText(handler), ReportTicket::getHandler, handler)
                .set(StringUtils.hasText(handleReason), ReportTicket::getHandleReason, handleReason)) > 0;
    }

    private ReportTicketVO toVO(ReportTicket e) {
        return ReportTicketVO.builder()
                .id(e.getId())
                .bizType(e.getBizType())
                .bizId(e.getBizId())
                .reporterId(e.getReporterId())
                .reason(e.getReason())
                .content(e.getContent())
                .status(e.getStatus())
                .handler(e.getHandler())
                .handleReason(e.getHandleReason())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}

package com.moyue.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.risk.domain.dto.query.AuditRecordQuery;
import com.moyue.risk.domain.entity.AuditRecord;
import com.moyue.risk.domain.vo.AuditRecordVO;
import com.moyue.risk.mapper.AuditRecordMapper;
import com.moyue.risk.service.AuditRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 风控域（审核工单）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecordServiceImpl implements AuditRecordService {

    private final AuditRecordMapper auditMapper;

    @Override
    public PageResult<AuditRecordVO> pageAudits(AuditRecordQuery query) {
        var page = PageUtils.<AuditRecord>page(query);
        var result = auditMapper.selectPage(page, new LambdaQueryWrapper<AuditRecord>()
                .like(StringUtils.hasText(query.getContent()), AuditRecord::getContent, query.getContent())
                .eq(query.getBizType() != null, AuditRecord::getBizType, query.getBizType())
                .eq(query.getStatus() != null, AuditRecord::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getBizId()), AuditRecord::getBizId, query.getBizId())
                .orderByDesc(AuditRecord::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAudit(AuditRecord entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(0);
        }
        auditMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAudit(AuditRecord entity) {
        AuditRecord exist = auditMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("审核工单");
        }
        return auditMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAudit(Long id) {
        AuditRecord exist = auditMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("审核工单");
        }
        return auditMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long id, String auditor) {
        AuditRecord exist = auditMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("审核工单");
        }
        return auditMapper.update(null, new LambdaUpdateWrapper<AuditRecord>()
                .eq(AuditRecord::getId, id)
                .set(AuditRecord::getStatus, 1)
                .set(StringUtils.hasText(auditor), AuditRecord::getAuditor, auditor)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long id, String auditor, String reason) {
        AuditRecord exist = auditMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("审核工单");
        }
        return auditMapper.update(null, new LambdaUpdateWrapper<AuditRecord>()
                .eq(AuditRecord::getId, id)
                .set(AuditRecord::getStatus, 2)
                .set(StringUtils.hasText(auditor), AuditRecord::getAuditor, auditor)
                .set(StringUtils.hasText(reason), AuditRecord::getReason, reason)) > 0;
    }

    private AuditRecordVO toVO(AuditRecord e) {
        return AuditRecordVO.builder()
                .id(e.getId())
                .bizType(e.getBizType())
                .bizId(e.getBizId())
                .content(e.getContent())
                .status(e.getStatus())
                .auditor(e.getAuditor())
                .reason(e.getReason())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}

package com.moyue.risk.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.risk.domain.dto.query.AuditRecordQuery;
import com.moyue.risk.domain.entity.AuditRecord;
import com.moyue.risk.domain.vo.AuditRecordVO;

/**
 * 风控域（审核工单）服务。
 *
 * @author moyue
 */
public interface AuditRecordService {

    PageResult<AuditRecordVO> pageAudits(AuditRecordQuery query);

    Long createAudit(AuditRecord entity);

    boolean updateAudit(AuditRecord entity);

    boolean deleteAudit(Long id);

    /** 审核通过 */
    boolean approve(Long id, String auditor);

    /** 审核驳回 */
    boolean reject(Long id, String auditor, String reason);
}

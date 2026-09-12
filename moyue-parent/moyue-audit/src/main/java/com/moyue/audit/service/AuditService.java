package com.moyue.audit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.audit.entity.AuditTaskEntity;
import com.moyue.audit.mapper.AuditTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审核任务业务：查询待投递审核任务。
 */
@Service
public class AuditService {

    @Autowired
    private AuditTaskMapper auditTaskMapper;

    /** 查询 status=0 的待投递任务（本地消息表未消费记录） */
    public List<AuditTaskEntity> listPending() {
        return auditTaskMapper.selectList(Wrappers.<AuditTaskEntity>lambdaQuery()
                .eq(AuditTaskEntity::getStatus, 0)
                .orderByAsc(AuditTaskEntity::getCreateTime));
    }
}

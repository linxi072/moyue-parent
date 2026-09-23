-- V10: 审核意见与操作人落库（技术债 16-20）
-- audit_task 通过 / 驳回时记录审核意见（remark）与操作人（operator_id，取网关注入 X-User-Id），
-- 事后可追溯「谁以何理由驳回」。audit_task 为本地消息表，无逻辑删除字段，不做软删。
ALTER TABLE audit_task
    ADD COLUMN remark VARCHAR(255) NULL COMMENT '审核意见（通过/驳回理由，可空）' AFTER retry_count,
    ADD COLUMN operator_id BIGINT NULL COMMENT '审核操作人用户 ID（网关注入，可空）' AFTER remark;

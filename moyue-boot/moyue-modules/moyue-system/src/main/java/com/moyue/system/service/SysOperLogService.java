package com.moyue.system.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.OperLogQuery;
import com.moyue.system.domain.entity.SysOperLog;

import java.util.List;
import java.util.Map;

/**
 * 操作日志域（⑦）服务。
 *
 * @author moyue
 */
public interface SysOperLogService {

    PageResult<SysOperLog> pageLogs(OperLogQuery query);

    SysOperLog detail(Long operId);

    boolean delete(Long operId);

    boolean deleteBatch(List<Long> ids);

    /** 清空全部（仅超管） */
    int clear();

    /** 导出的查询结果（最多 10000 行，避免大导出拖垮实例） */
    List<SysOperLog> listForExport(OperLogQuery query);

    /** 操作量统计：按日 / 按模块聚合 */
    Map<String, Object> stats(OperLogQuery query);
}

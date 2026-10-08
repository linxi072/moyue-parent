package com.moyue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.OperLogQuery;
import com.moyue.system.domain.entity.SysOperLog;
import com.moyue.system.mapper.SysOperLogMapper;
import com.moyue.system.service.SysOperLogService;
import com.moyue.system.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志域（⑦）实现：只查与清理，写入由 {@code @Log} 切面异步落库。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl implements SysOperLogService {

    /** 单次导出上限，防止大结果集打爆内存 */
    private static final long EXPORT_MAX = 10_000L;

    private final SysOperLogMapper operLogMapper;

    @Override
    public PageResult<SysOperLog> pageLogs(OperLogQuery query) {
        var page = PageUtils.<SysOperLog>page(query);
        var result = operLogMapper.selectPage(page, buildWrapper(query));
        return PageUtils.toResult(result);
    }

    @Override
    public SysOperLog detail(Long operId) {
        SysOperLog log = operLogMapper.selectById(operId);
        if (log == null) {
            throw BusinessException.notFound("操作日志");
        }
        return log;
    }

    @Override
    public boolean delete(Long operId) {
        return operLogMapper.deleteById(operId) > 0;
    }

    @Override
    public boolean deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return operLogMapper.deleteBatchIds(ids) > 0;
    }

    @Override
    public int clear() {
        // 逻辑删除列存在，清空走物理删除以真正释放空间
        return operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>().isNotNull(SysOperLog::getId));
    }

    @Override
    public List<SysOperLog> listForExport(OperLogQuery query) {
        Page<SysOperLog> page = new Page<>(1, EXPORT_MAX);
        return operLogMapper.selectPage(page, buildWrapper(query)).getRecords();
    }

    @Override
    public Map<String, Object> stats(OperLogQuery query) {
        Map<String, Object> stats = new LinkedHashMap<>();
        LambdaQueryWrapper<SysOperLog> wrapper = buildWrapper(query);
        stats.put("total", operLogMapper.selectCount(wrapper));
        stats.put("failTotal", operLogMapper.selectCount(
                buildWrapper(query).eq(SysOperLog::getStatus, 1)));
        // 按业务类型聚合
        Map<Integer, Long> byType = new LinkedHashMap<>();
        for (SysOperLog row : operLogMapper.selectList(buildWrapper(query))) {
            Integer type = row.getBusinessType() == null ? 0 : row.getBusinessType();
            byType.merge(type, 1L, Long::sum);
        }
        stats.put("byBusinessType", byType);
        return stats;
    }

    private LambdaQueryWrapper<SysOperLog> buildWrapper(OperLogQuery query) {
        return new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(query.getOperName()), SysOperLog::getOperName, query.getOperName())
                .like(StringUtils.hasText(query.getTitle()), SysOperLog::getTitle, query.getTitle())
                .eq(query.getBusinessType() != null, SysOperLog::getBusinessType, query.getBusinessType())
                .eq(query.getStatus() != null, SysOperLog::getStatus, query.getStatus())
                .ge(StringUtils.hasText(query.getBeginTime()), SysOperLog::getOperTime, query.getBeginTime())
                .le(StringUtils.hasText(query.getEndTime()), SysOperLog::getOperTime, query.getEndTime())
                .orderByDesc(SysOperLog::getOperTime);
    }
}

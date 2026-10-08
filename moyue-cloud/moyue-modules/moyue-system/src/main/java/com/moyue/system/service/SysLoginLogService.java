package com.moyue.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.moyue.common.core.result.PageResult;
import com.moyue.system.domain.dto.query.LoginLogQuery;
import com.moyue.system.domain.entity.SysLoginLog;

import java.util.List;

/**
 * 登录日志域（⑧）服务。
 *
 * @author moyue
 */
public interface SysLoginLogService extends IService<SysLoginLog> {

    PageResult<SysLoginLog> pageLogs(LoginLogQuery query);

    SysLoginLog detail(Long infoId);

    boolean deleteBatch(List<Long> ids);

    int clear();

    List<SysLoginLog> listForExport(LoginLogQuery query);

    /** 按账号查登录记录（含失败次数，供风控查看） */
    List<SysLoginLog> listByAccount(String username);

    /** 解锁被连续失败锁定的账号 */
    boolean unlock(String username);
}

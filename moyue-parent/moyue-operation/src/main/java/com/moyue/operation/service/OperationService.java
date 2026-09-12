package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.mapper.RewardOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 运营服务：打赏订单对账 / 公告占位实现。
 */
@Service
public class OperationService {

    @Autowired
    private RewardOrderMapper rewardOrderMapper;

    /**
     * 分页查询打赏订单（运营对账占位实现）。
     *
     * @param page 当前页（从 1 开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResult<RewardOrderEntity> listOrders(int page, int size) {
        Page<RewardOrderEntity> param = new Page<>(page, size);
        IPage<RewardOrderEntity> result = rewardOrderMapper.selectPage(param, null);
        PageResult<RewardOrderEntity> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }
}

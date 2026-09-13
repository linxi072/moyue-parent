package com.moyue.message.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.api.dto.PageResult;
import com.moyue.common.R;
import com.moyue.message.entity.MessageChannelRecordEntity;
import com.moyue.message.mapper.MessageChannelRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 触达记录管理端点（P2-14 N-4，role=3）：分页查询 message_channel_record。
 * 网关 {@code /api/v1/admin/**} 由 {@code AdminRoleInterceptor} 断言管理员角色。
 */
@RestController
@RequestMapping("/api/v1/admin/messages")
public class MessageRecordAdminController {

    @Autowired
    private MessageChannelRecordMapper messageChannelRecordMapper;

    /**
     * 触达记录分页查询。
     *
     * @param userId  可选：按接收用户过滤
     * @param channel 可选：按渠道过滤（1/2/3/4）
     * @param status  可选：按状态过滤（0 待发 / 1 成功 / 2 失败）
     * @param page    页码（默认 1）
     * @param size    每页大小（默认 20）
     */
    @GetMapping("/records")
    public R<PageResult<MessageChannelRecordEntity>> records(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer channel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<MessageChannelRecordEntity> p = new Page<>(page, size);
        LambdaQueryWrapper<MessageChannelRecordEntity> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(MessageChannelRecordEntity::getUserId, userId);
        }
        if (channel != null) {
            wrapper.eq(MessageChannelRecordEntity::getChannel, channel);
        }
        if (status != null) {
            wrapper.eq(MessageChannelRecordEntity::getStatus, status);
        }
        wrapper.orderByDesc(MessageChannelRecordEntity::getCreateTime);
        messageChannelRecordMapper.selectPage(p, wrapper);

        PageResult<MessageChannelRecordEntity> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPage((int) p.getCurrent());
        result.setSize((int) p.getSize());
        result.setRecords(p.getRecords());
        return R.ok(result);
    }
}

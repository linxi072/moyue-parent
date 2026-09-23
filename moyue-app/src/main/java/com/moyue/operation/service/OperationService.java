package com.moyue.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.core.domain.PageResult;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.operation.entity.AnnouncementEntity;
import com.moyue.operation.entity.RewardOrderEntity;
import com.moyue.operation.mapper.AnnouncementMapper;
import com.moyue.operation.mapper.RewardOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 运营服务：打赏订单对账 + 运营公告增删改查。
 * 公告写操作由 AdminRoleInterceptor（moyue-common）在 /api/v1/admin/** 上做角色断言（role=3）。
 */
@Service
public class OperationService {

    /** 公告状态：0 草稿 / 1 已发布 / 2 已下线 */
    private static final int ANNOUNCEMENT_PUBLISHED = 1;

    @Autowired
    private RewardOrderMapper rewardOrderMapper;

    @Autowired
    private AnnouncementMapper announcementMapper;

    // ------------------------------ 打赏订单对账 ------------------------------

    /**
     * 分页查询打赏订单（运营对账）。
     *
     * @param page 当前页（从 1 开始）
     * @param size 每页大小
     * @return 分页结果
     */
    public PageResult<RewardOrderEntity> listOrders(int page, int size) {
        Page<RewardOrderEntity> param = new Page<>(page, size);
        IPage<RewardOrderEntity> result = rewardOrderMapper.selectPage(param,
                Wrappers.<RewardOrderEntity>lambdaQuery().orderByDesc(RewardOrderEntity::getCreateTime));
        return toPageResult(result, page, size);
    }

    // ------------------------------ 运营公告 ------------------------------

    /** 分页查询公告，可按状态过滤，置顶优先、其余按发布时间倒序 */
    public PageResult<AnnouncementEntity> listAnnouncements(int page, int size, Integer status) {
        Page<AnnouncementEntity> param = new Page<>(page, size);
        IPage<AnnouncementEntity> result = announcementMapper.selectPage(param,
                Wrappers.<AnnouncementEntity>lambdaQuery()
                        .eq(status != null, AnnouncementEntity::getStatus, status)
                        .orderByDesc(AnnouncementEntity::getIsTop)
                        .orderByDesc(AnnouncementEntity::getCreateTime));
        return toPageResult(result, page, size);
    }

    /** 新建公告：status=1（已发布）时补发布时间 */
    @Transactional
    public AnnouncementEntity createAnnouncement(String title, String content, Integer type,
                                                 Integer isTop, Integer status) {
        if (title == null || title.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "正文不能为空");
        }
        AnnouncementEntity e = new AnnouncementEntity();
        e.setTitle(title.trim());
        e.setContent(content);
        e.setType(type == null ? 1 : type);
        e.setStatus(status == null ? 0 : status);
        e.setIsTop(isTop == null ? 0 : isTop);
        e.setIsDeleted(0);
        if (e.getStatus() == ANNOUNCEMENT_PUBLISHED) {
            e.setPublishTime(LocalDateTime.now());
        }
        announcementMapper.insert(e);
        return e;
    }

    /** 编辑公告：仅更新非空字段；由草稿转为已发布时补发布时间 */
    @Transactional
    public AnnouncementEntity updateAnnouncement(Long id, String title, String content, Integer type,
                                                 Integer isTop, Integer status) {
        AnnouncementEntity e = announcementMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        if (title != null) {
            if (title.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "标题不能为空");
            }
            e.setTitle(title.trim());
        }
        if (content != null) {
            if (content.isBlank()) {
                throw new BizException(ResultCode.PARAM_ERROR, "正文不能为空");
            }
            e.setContent(content);
        }
        if (type != null) {
            e.setType(type);
        }
        if (isTop != null) {
            e.setIsTop(isTop);
        }
        if (status != null) {
            e.setStatus(status);
            if (status == ANNOUNCEMENT_PUBLISHED && e.getPublishTime() == null) {
                e.setPublishTime(LocalDateTime.now());
            }
        }
        announcementMapper.updateById(e);
        return e;
    }

    /** 删除公告（逻辑删除） */
    @Transactional
    public void deleteAnnouncement(Long id) {
        AnnouncementEntity e = announcementMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        announcementMapper.deleteById(id);
    }

    // ------------------------------ 工具 ------------------------------

    private <T> PageResult<T> toPageResult(IPage<T> result, int page, int size) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setRecords(result.getRecords());
        return pageResult;
    }
}

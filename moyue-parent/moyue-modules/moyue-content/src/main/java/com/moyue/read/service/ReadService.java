package com.moyue.read.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.moyue.api.commerce.PointsClient;
import com.moyue.api.commerce.dto.PointsAwardDTO;
import com.moyue.common.exception.BizException;
import com.moyue.common.core.domain.R;
import com.moyue.common.core.domain.ResultCode;
import com.moyue.common.core.constants.CacheNames;
import com.moyue.read.entity.BookshelfEntity;
import com.moyue.read.mapper.BookshelfMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅读业务：书架查询与增删、阅读进度更新、阅读时长上报（P1-10 生产者侧）。
 */
@Service
public class ReadService {

    private static final Logger log = LoggerFactory.getLogger(ReadService.class);

    /** 单次上报时长上限（分钟），防异常刷分 */
    private static final int MAX_MINUTES_PER_REPORT = 120;

    /** 每阅读 10 分钟奖励 1 积分（bizType=2） */
    private static final int MINUTES_PER_POINT = 10;

    /** 阅读时长奖励 bizType（与 points_flow.biz_type 注释对齐） */
    private static final int BIZ_READ_DURATION = 2;

    @Autowired
    private BookshelfMapper bookshelfMapper;

    /** 积分服务客户端；不可用时阅读主流程不受影响（奖励降级跳过） */
    @Autowired(required = false)
    private PointsClient pointsClient;

    /**
     * 按用户 ID 查询书架（按加入时间倒序）。
     * 缓存名 {@link CacheNames#READ_BOOKSHELF}（TTL 5 分钟），key = userId。
     * 加/移书架与翻章更新进度都会失效该 key，故连续阅读时命中率偏低，
     * 但书架页属「打开一次看很久」的场景，仍值得缓存。
     */
    @Cacheable(cacheNames = CacheNames.READ_BOOKSHELF, key = "#userId")
    public List<BookshelfEntity> getShelf(Long userId) {
        QueryWrapper<BookshelfEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        qw.orderByDesc("create_time");
        return bookshelfMapper.selectList(qw);
    }

    /**
     * 加入书架：唯一键 uk_user_book 会被逻辑删除行占用，故以「insert，冲突则复活」保证幂等，
     * 避免「收藏 → 取消 → 再收藏」时撞唯一键。
     */
    @CacheEvict(cacheNames = CacheNames.READ_BOOKSHELF, key = "#userId")
    @Transactional
    public void addToShelf(Long userId, Long bookId) {
        if (bookId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "书籍 ID 不能为空");
        }
        BookshelfEntity e = new BookshelfEntity();
        e.setUserId(userId);
        e.setBookId(bookId);
        e.setIsDeleted(0);
        e.setCreateTime(LocalDateTime.now());
        try {
            bookshelfMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 已收藏，或曾取消收藏留下逻辑删除行：复活旧记录，保持幂等
            bookshelfMapper.revive(userId, bookId);
        }
    }

    /** 移出书架（全局逻辑删除） */
    @CacheEvict(cacheNames = CacheNames.READ_BOOKSHELF, key = "#userId")
    @Transactional
    public void removeFromShelf(Long userId, Long bookId) {
        BookshelfEntity e = findActive(userId, bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        bookshelfMapper.deleteById(e.getId());
    }

    /**
     * 更新阅读进度（最后阅读章节）。
     * 翻章属于高频写，且进度随 {@link #getShelf(Long)} 一并返回，故不做独立缓存，只失效书架缓存。
     */
    @CacheEvict(cacheNames = CacheNames.READ_BOOKSHELF, key = "#userId")
    @Transactional
    public void updateProgress(Long userId, Long bookId, Long chapterId) {
        if (chapterId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "章节 ID 不能为空");
        }
        BookshelfEntity e = findActive(userId, bookId);
        if (e == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND);
        }
        e.setLastChapterId(chapterId);
        bookshelfMapper.updateById(e);
    }

    /**
     * 阅读时长上报（P1-10 生产者侧）：按 10 分钟 = 1 积分发放到积分服务（bizType=2）。
     * 单次上限 120 分钟；积分服务不可用 / 发放失败仅记日志，不阻断阅读主流程（奖励是旁路）。
     *
     * @return 本次实际发放的积分数（发放失败返回 0）
     */
    public int reportDuration(Long userId, Long bookId, Integer minutes) {
        if (minutes == null || minutes <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "阅读时长必须大于 0 分钟");
        }
        if (minutes > MAX_MINUTES_PER_REPORT) {
            throw new BizException(ResultCode.PARAM_ERROR, "单次上报时长不能超过 " + MAX_MINUTES_PER_REPORT + " 分钟");
        }
        int points = Math.max(1, minutes / MINUTES_PER_POINT);
        if (pointsClient == null) {
            log.warn("[read] 积分服务不可用，阅读时长奖励跳过：userId={}, minutes={}", userId, minutes);
            return 0;
        }
        try {
            PointsAwardDTO award = new PointsAwardDTO();
            award.setUserId(userId);
            award.setBizType(BIZ_READ_DURATION);
            award.setPoints(points);
            award.setRemark("阅读时长奖励：" + minutes + " 分钟");
            R<Integer> resp = pointsClient.award(award);
            // 关键：Feign 不抛业务异常（HTTP 200 + R.code != 0），必须显式校验 code
            if (resp == null || resp.getCode() != ResultCode.SUCCESS.getCode()) {
                log.warn("[read] 阅读时长奖励发放失败（已忽略）：userId={}, resp={}", userId, resp);
                return 0;
            }
            return points;
        } catch (Exception ex) {
            log.warn("[read] 阅读时长奖励调用异常（已忽略）：userId={}, minutes={}", userId, minutes, ex);
            return 0;
        }
    }

    /** 查当前用户在该书上的有效书架行（全局逻辑删除自动过滤已移除行） */
    private BookshelfEntity findActive(Long userId, Long bookId) {
        QueryWrapper<BookshelfEntity> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("book_id", bookId);
        return bookshelfMapper.selectOne(qw);
    }
}

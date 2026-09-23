package com.moyue.follow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.social.dto.FollowDTO;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.core.domain.CursorPageResult;
import com.moyue.follow.entity.FollowEntity;
import com.moyue.follow.mapper.FollowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关注关系业务（P2-E）：关注 / 取关（逻辑删）/ 重关注（revive）/ 关注列表 / 粉丝列表（游标分页）/ 互关识别。
 *
 * <p>游标基于 {@code (create_time DESC, id DESC)} keyset；响应含 {@code nextCursor} / {@code hasMore}，
 * 禁深 OFFSET。findFollowedAuthorIds 返回 is_deleted=0 的关注作者 ID，供 feed 时间线聚合。</p>
 */
@Service
public class FollowService {

    /** 列表 size 上限保护 */
    private static final int MAX_SIZE = 50;

    /** 默认每页 */
    private static final int DEFAULT_SIZE = 20;

    @Autowired
    private FollowMapper followMapper;

    /** 关注作者：userId 取自网关注入头，绝不取请求体；不能关注自己。 */
    @Transactional
    public FollowDTO follow(Long fanId, Long authorId) {
        if (fanId == null || authorId == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        if (fanId.equals(authorId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能关注自己");
        }
        FollowEntity e = new FollowEntity();
        e.setFanId(fanId);
        e.setAuthorId(authorId);
        e.setIsDeleted(0);
        try {
            followMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 曾关注并取关过（uk_follow_pair 仍占用）：复活旧记录，避免产生重复行
            followMapper.revive(fanId, authorId);
            FollowEntity existing = followMapper.selectOne(Wrappers.<FollowEntity>lambdaQuery()
                    .eq(FollowEntity::getFanId, fanId)
                    .eq(FollowEntity::getAuthorId, authorId));
            return toDto(existing);
        }
        return toDto(e);
    }

    /** 取关（逻辑删除 is_deleted=1） */
    @Transactional
    public void unfollow(Long fanId, Long authorId) {
        if (fanId == null || authorId == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        followMapper.delete(Wrappers.<FollowEntity>lambdaUpdate()
                .eq(FollowEntity::getFanId, fanId)
                .eq(FollowEntity::getAuthorId, authorId));
    }

    /** 我关注的人：游标分页（create_time DESC, id DESC） */
    public CursorPageResult<FollowDTO> listFollowing(Long fanId, String cursor, int size) {
        int limit = normalizeSize(size);
        List<FollowEntity> list = followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFanId, fanId)
                .apply(cursor != null && !cursor.isBlank(), buildCursorSql(), parseCursor(cursor))
                .orderByDesc(FollowEntity::getCreateTime)
                .orderByDesc(FollowEntity::getId)
                .last("LIMIT " + (limit + 1)));
        return toCursorPage(list, limit, FollowService::toDto);
    }

    /** 我的粉丝：游标分页（create_time DESC, id DESC） */
    public CursorPageResult<FollowDTO> listFollowers(Long authorId, String cursor, int size) {
        int limit = normalizeSize(size);
        List<FollowEntity> list = followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getAuthorId, authorId)
                .apply(cursor != null && !cursor.isBlank(), buildCursorSql(), parseCursor(cursor))
                .orderByDesc(FollowEntity::getCreateTime)
                .orderByDesc(FollowEntity::getId)
                .last("LIMIT " + (limit + 1)));
        return toCursorPage(list, limit, FollowService::toDto);
    }

    /** 互关识别：a 关注 b 且 b 关注 a（双向均有效） */
    public boolean isMutual(Long a, Long b) {
        boolean aFollowsB = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFanId, a).eq(FollowEntity::getAuthorId, b)) > 0;
        if (!aFollowsB) {
            return false;
        }
        return followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFanId, b).eq(FollowEntity::getAuthorId, a)) > 0;
    }

    /** 已关注且未软删的作者 ID 列表（供 feed 时间线聚合） */
    public List<Long> findFollowedAuthorIds(Long fanId) {
        List<FollowEntity> list = followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFanId, fanId)
                .select(FollowEntity::getAuthorId));
        return list.stream().map(FollowEntity::getAuthorId).collect(Collectors.toList());
    }

    // ------------------------------ 游标 & 转换 ------------------------------

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 构造 keyset 游标 SQL（H2 / MySQL 兼容）：(create_time < {0}) OR (create_time = {1} AND id < {2})，{0}/{1}/{2} 对应 parseCursor 的 [ct, ct, id] */
    private String buildCursorSql() {
        return "(create_time < {0}) OR (create_time = {1} AND id < {2})";
    }

    /** 解析 cursor = createTimeMillis_id 为 [LocalDateTime, LocalDateTime, Long]（ct 复用两次） */
    private Object[] parseCursor(String cursor) {
        // 首屏 cursor 为 null/空串时直接返回 null（apply 条件为假不会使用该参数，但参数会先行求值）
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = cursor.split("_");
        long millis = Long.parseLong(parts[0]);
        long id = Long.parseLong(parts[1]);
        LocalDateTime ct = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        return new Object[]{ct, ct, id};
    }

    private CursorPageResult<FollowDTO> toCursorPage(List<FollowEntity> list, int limit,
                                                    Function<FollowEntity, FollowDTO> mapper) {
        CursorPageResult<FollowDTO> result = new CursorPageResult<>();
        boolean hasMore = list.size() > limit;
        List<FollowEntity> page = hasMore ? list.subList(0, limit) : list;
        result.setHasMore(hasMore);
        result.setRecords(page.stream().map(mapper).collect(Collectors.toList()));
        if (!page.isEmpty()) {
            FollowEntity last = page.get(page.size() - 1);
            result.setNextCursor(encodeCursor(last.getCreateTime(), last.getId()));
        }
        return result;
    }

    static String encodeCursor(LocalDateTime ct, Long id) {
        long millis = ct.toInstant(ZoneOffset.UTC).toEpochMilli();
        return millis + "_" + id;
    }

    static FollowDTO toDto(FollowEntity e) {
        if (e == null) {
            return null;
        }
        FollowDTO d = new FollowDTO();
        d.setId(e.getId());
        d.setFanId(e.getFanId());
        d.setAuthorId(e.getAuthorId());
        d.setCreateTime(e.getCreateTime());
        return d;
    }
}

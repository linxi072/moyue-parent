package com.moyue.dynamic.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyue.api.social.dto.DynamicDTO;
import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.common.core.domain.CursorPageResult;
import com.moyue.dynamic.entity.DynamicEntity;
import com.moyue.dynamic.mapper.DynamicMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户动态业务（P2-E）：幂等发布 / 粉丝时间线 feed（fan-out-on-read）/ 作者主页动态页。
 *
 * <p>feed 取关即失效、无需物化表：timeline 直接对 {@code user_dynamic} 按已关注作者集合做 keyset 游标查询。
 * 游标基于 {@code (create_time DESC, id DESC)}，响应含 {@code nextCursor} / {@code hasMore}。</p>
 */
@Service
public class DynamicService {

    /** 列表 size 上限保护 */
    private static final int MAX_SIZE = 50;

    /** 默认每页 */
    private static final int DEFAULT_SIZE = 20;

    @Autowired
    private DynamicMapper dynamicMapper;

    /**
     * 幂等发布动态（内部端点调用）：按 (ref_type, ref_id) 探测历史，
     * 存在则刷新反规范化快照并复活，否则插入新行。
     */
    public DynamicDTO create(DynamicPublishDTO dto) {
        DynamicEntity e = toEntity(dto);
        Integer cnt = dynamicMapper.countByRef(dto.getRefType(), dto.getRefId());
        if (cnt != null && cnt > 0) {
            dynamicMapper.upsertByRef(e);
            DynamicEntity existing = dynamicMapper.selectOne(Wrappers.<DynamicEntity>lambdaQuery()
                    .eq(DynamicEntity::getRefType, dto.getRefType())
                    .eq(DynamicEntity::getRefId, dto.getRefId()));
            return toDto(existing);
        }
        dynamicMapper.insert(e);
        return toDto(e);
    }

    /**
     * 粉丝时间线 feed：对关注作者集合做 keyset 游标查询（fan-out-on-read）。
     * 作者集合为空直接返回空页（不落库、不报错）。MP 自动过滤 is_deleted=0。
     */
    public CursorPageResult<DynamicDTO> timeline(List<Long> authorIds, String cursor, int size) {
        int limit = normalizeSize(size);
        if (authorIds == null || authorIds.isEmpty()) {
            return emptyPage();
        }
        List<DynamicEntity> list = dynamicMapper.selectList(Wrappers.<DynamicEntity>lambdaQuery()
                .in(DynamicEntity::getAuthorId, authorIds)
                .apply(cursor != null && !cursor.isBlank(), buildCursorSql(), parseCursor(cursor))
                .orderByDesc(DynamicEntity::getCreateTime)
                .orderByDesc(DynamicEntity::getId)
                .last("LIMIT " + (limit + 1)));
        return toCursorPage(list, limit, DynamicService::toDto);
    }

    /** 作者主页动态页：按作者游标分页（MP 自动过滤 is_deleted=0） */
    public CursorPageResult<DynamicDTO> pageByAuthor(Long authorId, String cursor, int size) {
        int limit = normalizeSize(size);
        List<DynamicEntity> list = dynamicMapper.selectList(Wrappers.<DynamicEntity>lambdaQuery()
                .eq(DynamicEntity::getAuthorId, authorId)
                .apply(cursor != null && !cursor.isBlank(), buildCursorSql(), parseCursor(cursor))
                .orderByDesc(DynamicEntity::getCreateTime)
                .orderByDesc(DynamicEntity::getId)
                .last("LIMIT " + (limit + 1)));
        return toCursorPage(list, limit, DynamicService::toDto);
    }

    // ------------------------------ 游标 & 转换 ------------------------------

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private CursorPageResult<DynamicDTO> emptyPage() {
        CursorPageResult<DynamicDTO> result = new CursorPageResult<>();
        result.setHasMore(false);
        result.setRecords(java.util.Collections.emptyList());
        return result;
    }

    /** 构造 keyset 游标 SQL（H2 / MySQL 兼容）：(create_time < ?) OR (create_time = ? AND id < ?) */
    private String buildCursorSql() {
        return "(create_time < ?) OR (create_time = ? AND id < ?)";
    }

    /** 解析 cursor = createTimeMillis_id 为 [LocalDateTime, LocalDateTime, Long]（ct 复用两次） */
    private Object[] parseCursor(String cursor) {
        String[] parts = cursor.split("_");
        long millis = Long.parseLong(parts[0]);
        long id = Long.parseLong(parts[1]);
        LocalDateTime ct = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        return new Object[]{ct, ct, id};
    }

    private CursorPageResult<DynamicDTO> toCursorPage(List<DynamicEntity> list, int limit,
                                                     Function<DynamicEntity, DynamicDTO> mapper) {
        CursorPageResult<DynamicDTO> result = new CursorPageResult<>();
        boolean hasMore = list.size() > limit;
        List<DynamicEntity> page = hasMore ? list.subList(0, limit) : list;
        result.setHasMore(hasMore);
        result.setRecords(page.stream().map(mapper).collect(Collectors.toList()));
        if (!page.isEmpty()) {
            DynamicEntity last = page.get(page.size() - 1);
            result.setNextCursor(encodeCursor(last.getCreateTime(), last.getId()));
        }
        return result;
    }

    static String encodeCursor(LocalDateTime ct, Long id) {
        long millis = ct.toInstant(ZoneOffset.UTC).toEpochMilli();
        return millis + "_" + id;
    }

    static DynamicEntity toEntity(DynamicPublishDTO dto) {
        DynamicEntity e = new DynamicEntity();
        e.setActorUserId(dto.getActorUserId());
        e.setActorName(dto.getActorName());
        e.setAuthorId(dto.getAuthorId());
        e.setAuthorName(dto.getAuthorName());
        e.setBookId(dto.getBookId());
        e.setBookTitle(dto.getBookTitle());
        e.setDynamicType(dto.getDynamicType());
        e.setSummary(dto.getSummary());
        e.setRefId(dto.getRefId());
        e.setRefType(dto.getRefType());
        e.setIsDeleted(0);
        return e;
    }

    static DynamicDTO toDto(DynamicEntity e) {
        if (e == null) {
            return null;
        }
        DynamicDTO d = new DynamicDTO();
        d.setId(e.getId());
        d.setActorUserId(e.getActorUserId());
        d.setActorName(e.getActorName());
        d.setAuthorId(e.getAuthorId());
        d.setAuthorName(e.getAuthorName());
        d.setBookId(e.getBookId());
        d.setBookTitle(e.getBookTitle());
        d.setDynamicType(e.getDynamicType());
        d.setSummary(e.getSummary());
        d.setRefId(e.getRefId());
        d.setCreateTime(e.getCreateTime());
        return d;
    }
}

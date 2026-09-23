package com.moyue.dynamic.service;

import com.moyue.api.social.dto.DynamicDTO;
import com.moyue.api.social.dto.DynamicPublishDTO;
import com.moyue.common.core.domain.CursorPageResult;
import com.moyue.dynamic.entity.DynamicEntity;
import com.moyue.dynamic.mapper.DynamicMapper;
import com.moyue.follow.service.FollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户动态业务集成测试（P2-E）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；装配 {@link DynamicService} / {@link FollowService} 走真实 Mapper。
 * 覆盖：feed 时间线聚合（仅已关注作者、按时间倒序、未关注作者的动态不出现）、游标分页（无重复/遗漏）、
 * 软删动态从流中消失、幂等发布（同 ref 仅 1 行）、作者主页动态页。
 * profile 固定为 {@code test}（见 src/test/resources/application-test.yml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class DynamicServiceTest {

    @Autowired
    private DynamicService dynamicService;

    @Autowired
    private FollowService followService;

    @Autowired
    private DynamicMapper dynamicMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 自增 refId 序列，保证种子动态 (ref_type, ref_id) 唯一 */
    private long refSeq = 800000L;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM user_dynamic");
        jdbcTemplate.update("DELETE FROM follow_relation");
    }

    /** 直接向 user_dynamic 插入一条动态（显式 create_time 保证时间倒序可断言） */
    private DynamicEntity seedDynamic(Long authorId, int hoursAgo, Integer dynamicType) {
        DynamicEntity e = new DynamicEntity();
        e.setAuthorId(authorId);
        e.setDynamicType(dynamicType);
        e.setRefType(1);
        e.setRefId(refSeq++);
        e.setCreateTime(LocalDateTime.now().minusHours(hoursAgo));
        e.setIsDeleted(0);
        dynamicMapper.insert(e);
        return e;
    }

    @Test
    void timeline_aggregatesOnlyFollowedAuthor() {
        followService.follow(100L, 200L); // fan 100 关注 author 200；不关注 201
        DynamicEntity a1 = seedDynamic(200L, 3, 1);
        DynamicEntity a2 = seedDynamic(200L, 2, 1);
        DynamicEntity a3 = seedDynamic(200L, 1, 1);
        seedDynamic(201L, 1, 1); // 未关注作者的动态不出现

        List<Long> followed = followService.findFollowedAuthorIds(100L);
        assertThat(followed).containsExactly(200L);

        CursorPageResult<DynamicDTO> page = dynamicService.timeline(followed, null, 20);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getRecords()).extracting(DynamicDTO::getAuthorId).containsOnly(200L);
        // 时间倒序：最近（hoursAgo=1）排在最前
        assertThat(page.getRecords().get(0).getId()).isEqualTo(a3.getId());
    }

    @Test
    void timeline_cursorPagination_noDuplicates() {
        followService.follow(100L, 200L);
        DynamicEntity a1 = seedDynamic(200L, 3, 1);
        DynamicEntity a2 = seedDynamic(200L, 2, 1);
        DynamicEntity a3 = seedDynamic(200L, 1, 1);

        CursorPageResult<DynamicDTO> page1 = dynamicService.timeline(
                followService.findFollowedAuthorIds(100L), null, 2);
        assertThat(page1.getRecords()).hasSize(2);
        assertThat(page1.isHasMore()).isTrue();
        assertThat(page1.getNextCursor()).isNotNull();

        CursorPageResult<DynamicDTO> page2 = dynamicService.timeline(
                followService.findFollowedAuthorIds(100L), page1.getNextCursor(), 2);
        assertThat(page2.getRecords()).hasSize(1);
        assertThat(page2.isHasMore()).isFalse();

        List<Long> all = new ArrayList<>();
        page1.getRecords().forEach(d -> all.add(d.getId()));
        page2.getRecords().forEach(d -> all.add(d.getId()));
        assertThat(all).containsExactlyInAnyOrder(a1.getId(), a2.getId(), a3.getId());
        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    void timeline_excludesSoftDeleted() {
        followService.follow(100L, 200L);
        DynamicEntity keep = seedDynamic(200L, 2, 1);
        DynamicEntity removed = seedDynamic(200L, 1, 1);

        CursorPageResult<DynamicDTO> before = dynamicService.timeline(
                followService.findFollowedAuthorIds(100L), null, 20);
        assertThat(before.getRecords()).hasSize(2);

        // 软删 removed：动态经 @TableLogic 自动过滤 is_deleted=0
        jdbcTemplate.update("UPDATE user_dynamic SET is_deleted = 1 WHERE id = ?", removed.getId());

        CursorPageResult<DynamicDTO> after = dynamicService.timeline(
                followService.findFollowedAuthorIds(100L), null, 20);
        assertThat(after.getRecords()).hasSize(1);
        assertThat(after.getRecords().get(0).getId()).isEqualTo(keep.getId());
    }

    @Test
    void create_isIdempotent_singleRow() {
        DynamicPublishDTO dto = new DynamicPublishDTO();
        dto.setActorUserId(50L);
        dto.setAuthorId(200L);
        dto.setAuthorName("作者X");
        dto.setBookId(9001L);
        dto.setBookTitle("测试书");
        dto.setDynamicType(1);
        dto.setSummary("发布新作");
        dto.setRefId(5001L);
        dto.setRefType(1);

        DynamicDTO d1 = dynamicService.create(dto);
        DynamicDTO d2 = dynamicService.create(dto); // 同 (ref_type, ref_id) 重复回调用例
        assertThat(d1.getId()).isEqualTo(d2.getId());

        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_dynamic WHERE ref_type = 1 AND ref_id = 5001", Integer.class);
        assertThat(cnt).isEqualTo(1);
    }

    @Test
    void pageByAuthor_returnsOnlyAuthorsDynamics() {
        seedDynamic(200L, 2, 1);
        seedDynamic(200L, 1, 1);
        seedDynamic(201L, 1, 1);

        CursorPageResult<DynamicDTO> page = dynamicService.pageByAuthor(200L, null, 20);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).extracting(DynamicDTO::getAuthorId).containsOnly(200L);
    }
}

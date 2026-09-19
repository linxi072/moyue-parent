package com.moyue.follow.service;

import com.moyue.api.social.dto.FollowDTO;
import com.moyue.common.BizException;
import com.moyue.common.core.domain.CursorPageResult;
import com.moyue.follow.entity.FollowEntity;
import com.moyue.follow.mapper.FollowMapper;
import com.moyue.social.SocialApplication;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 关注关系业务集成测试（P2-E）。
 * H2 内存库（MySQL 兼容模式）+ Flyway 全量建表；直接装配 {@link FollowService} 走真实 Mapper。
 * 覆盖：关注 → 列表可见 → 互关识别 → 取关 → 列表不可见 → 重关注（revive，无重复行）；
 * 关注自己被拒；粉丝列表游标分页（无重复/遗漏）。
 * profile 固定为 {@code test}（见 src/test/resources/application-test.yml）。
 */
@SpringBootTest(classes = SocialApplication.class)
@ActiveProfiles("test")
class FollowServiceTest {

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // follow_relation / user_dynamic 为 P2-E 新增表，其它测试不触碰，清空保证用例隔离
        jdbcTemplate.update("DELETE FROM follow_relation");
    }

    @Test
    void follow_thenListFollowing_andUnfollow() {
        followService.follow(1L, 2L);
        CursorPageResult<FollowDTO> following = followService.listFollowing(1L, null, 20);
        assertThat(following.getRecords()).extracting(FollowDTO::getAuthorId).contains(2L);

        followService.unfollow(1L, 2L);
        CursorPageResult<FollowDTO> after = followService.listFollowing(1L, null, 20);
        assertThat(after.getRecords()).extracting(FollowDTO::getAuthorId).doesNotContain(2L);
        assertThat(followService.findFollowedAuthorIds(1L)).doesNotContain(2L);
    }

    @Test
    void follow_self_isRejected() {
        assertThatThrownBy(() -> followService.follow(1L, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void refollow_revives_existingRow_noDuplicate() {
        followService.follow(1L, 2L);
        assertThat(activeRows(1L, 2L)).isEqualTo(1);

        followService.unfollow(1L, 2L);
        assertThat(activeRows(1L, 2L)).isEqualTo(0);

        // 重关注：捕获 uk_follow_pair 冲突后 revive，物理行仍只有 1 条
        followService.follow(1L, 2L);
        assertThat(totalRows(1L, 2L)).isEqualTo(1);
        assertThat(activeRows(1L, 2L)).isEqualTo(1);

        CursorPageResult<FollowDTO> following = followService.listFollowing(1L, null, 20);
        assertThat(following.getRecords()).extracting(FollowDTO::getAuthorId).containsExactly(2L);
    }

    @Test
    void isMutual_trueOnlyWhenBidirectional() {
        followService.follow(1L, 2L);
        assertThat(followService.isMutual(1L, 2L)).isFalse();

        followService.follow(2L, 1L);
        assertThat(followService.isMutual(1L, 2L)).isTrue();

        followService.unfollow(2L, 1L);
        assertThat(followService.isMutual(1L, 2L)).isFalse();
    }

    @Test
    void listFollowers_cursorPagination_noDuplicates() {
        // 作者 2 的粉丝：3 / 4 / 5 / 6 / 7，显式 create_time 保证排序键确定（避免 DB CURRENT_TIMESTAMP 同刻抖动导致偶发丢行）
        seedFollower(2L, 3L, 4);
        seedFollower(2L, 4L, 3);
        seedFollower(2L, 5L, 2);
        seedFollower(2L, 6L, 1);
        seedFollower(2L, 7L, 0);

        CursorPageResult<FollowDTO> page1 = followService.listFollowers(2L, null, 2);
        assertThat(page1.getRecords()).hasSize(2);
        assertThat(page1.isHasMore()).isTrue();
        assertThat(page1.getNextCursor()).isNotNull();

        CursorPageResult<FollowDTO> page2 = followService.listFollowers(2L, page1.getNextCursor(), 2);
        CursorPageResult<FollowDTO> page3 = followService.listFollowers(2L, page2.getNextCursor(), 2);

        List<Long> all = new ArrayList<>();
        page1.getRecords().forEach(d -> all.add(d.getFanId()));
        page2.getRecords().forEach(d -> all.add(d.getFanId()));
        page3.getRecords().forEach(d -> all.add(d.getFanId()));
        assertThat(all).containsExactlyInAnyOrder(3L, 4L, 5L, 6L, 7L);
        assertThat(all).doesNotHaveDuplicates();
    }

    /** 直接落库一条关注关系并显式指定 create_time（绕过 follow() 的 DB 默认时间戳，使排序键确定） */
    private FollowEntity seedFollower(Long authorId, Long fanId, int hoursAgo) {
        FollowEntity e = new FollowEntity();
        e.setFanId(fanId);
        e.setAuthorId(authorId);
        e.setIsDeleted(0);
        e.setCreateTime(LocalDateTime.now().minusHours(hoursAgo));
        followMapper.insert(e);
        return e;
    }

    private int activeRows(long fanId, long authorId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follow_relation WHERE fan_id = ? AND author_id = ? AND is_deleted = 0",
                Integer.class, fanId, authorId);
    }

    private int totalRows(long fanId, long authorId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follow_relation WHERE fan_id = ? AND author_id = ?",
                Integer.class, fanId, authorId);
    }
}

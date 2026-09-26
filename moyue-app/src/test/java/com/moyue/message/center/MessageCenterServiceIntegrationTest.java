package com.moyue.message.center;

import com.moyue.common.cache.CacheNames;
import com.moyue.common.cache.LocalCacheTestConfig;
import com.moyue.common.core.domain.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息中心服务集成测试（H2 + 进程内缓存）。
 * 覆盖：收件箱聚合（合并站内信与公告）、按类型筛选、仅未读筛选、未读角标、标记已读（含缓存失效）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalCacheTestConfig.class)
public class MessageCenterServiceIntegrationTest {

    private static final long USER = 9001L;

    // 站内信固定 ID，便于标记已读时精确引用
    private static final long N_UNREAD_SYS_1 = 9101L;
    private static final long N_UNREAD_SYS_2 = 9102L;
    private static final long N_READ_INT = 9103L;
    private static final long N_DELETED = 9104L;
    // 公告固定 ID
    private static final long A_PUB_1 = 9201L;
    private static final long A_PUB_2 = 9202L;
    private static final long A_DRAFT = 9203L;

    @Autowired
    private MessageCenterService messageCenterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM notice WHERE user_id = ?", USER);
        jdbcTemplate.update("DELETE FROM announcement");

        // 有效站内信 3 条（user_id=9001）：2 条未读系统 + 1 条已读互动
        jdbcTemplate.update("INSERT INTO notice (id, user_id, title, content, type, is_read, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                N_UNREAD_SYS_1, USER, "系统通知1", "内容1", 1, 0, 0, "2026-01-01 10:00:00");
        jdbcTemplate.update("INSERT INTO notice (id, user_id, title, content, type, is_read, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                N_UNREAD_SYS_2, USER, "系统通知2", "内容2", 1, 0, 0, "2026-01-02 10:00:00");
        jdbcTemplate.update("INSERT INTO notice (id, user_id, title, content, type, is_read, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                N_READ_INT, USER, "互动消息", "内容3", 2, 1, 0, "2026-01-03 10:00:00");
        // 已逻辑删除的站内信：验证被 is_deleted = 0 过滤
        jdbcTemplate.update("INSERT INTO notice (id, user_id, title, content, type, is_read, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                N_DELETED, USER, "已删除", "内容4", 1, 0, 1, "2026-01-04 10:00:00");

        // 已发布公告 2 条 + 草稿 1 条（草稿 status=0，不进收件箱）
        jdbcTemplate.update("INSERT INTO announcement (id, title, content, type, status, is_top, publish_time, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                A_PUB_1, "公告A", "公告内容A", 1, 1, 0, "2026-01-05 10:00:00", 0, "2026-01-05 09:00:00");
        jdbcTemplate.update("INSERT INTO announcement (id, title, content, type, status, is_top, publish_time, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                A_PUB_2, "公告B", "公告内容B", 1, 1, 0, "2026-01-06 10:00:00", 0, "2026-01-06 09:00:00");
        jdbcTemplate.update("INSERT INTO announcement (id, title, content, type, status, is_top, publish_time, is_deleted, create_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                A_DRAFT, "草稿公告", "草稿", 1, 0, 0, "2026-01-07 10:00:00", 0, "2026-01-07 09:00:00");

        if (cacheManager.getCache(CacheNames.INBOX) != null) {
            cacheManager.getCache(CacheNames.INBOX).clear();
        }
    }

    @Test
    @DisplayName("收件箱聚合：合并有效站内信(3) + 已发布公告(2) = 5，排除已删站内信与草稿公告，按时间倒序")
    void listInbox_mergeAndOrder() {
        PageResult<InboxItemDTO> page = messageCenterService.listInbox(USER, null, false, 1, 20);

        assertThat(page.getTotal()).isEqualTo(5);
        List<InboxItemDTO> records = page.getRecords();
        assertThat(records).hasSize(5);

        // 时间倒序：最新为公告B（2026-01-06），最旧为系统通知1（2026-01-01）
        assertThat(records.get(0).getSource()).isEqualTo("ANNOUNCEMENT");
        assertThat(records.get(0).getId()).isEqualTo(A_PUB_2);
        assertThat(records.get(records.size() - 1).getSource()).isEqualTo("NOTICE");
        assertThat(records.get(records.size() - 1).getId()).isEqualTo(N_UNREAD_SYS_1);

        // 不含已删站内信 / 草稿公告
        assertThat(records).noneMatch(i -> N_DELETED == (i.getId() == null ? -1 : i.getId()));
        assertThat(records).noneMatch(i -> A_DRAFT == (i.getId() == null ? -1 : i.getId()));
        // 公告恒为已读
        assertThat(records.stream().filter(i -> "ANNOUNCEMENT".equals(i.getSource())).allMatch(InboxItemDTO::getRead)).isTrue();
    }

    @Test
    @DisplayName("按类型筛选 type=1：仅返回未删的系统站内信（2 条），不含公告")
    void listInbox_filterTypeSystem() {
        PageResult<InboxItemDTO> page = messageCenterService.listInbox(USER, 1, false, 1, 20);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).allMatch(i -> "NOTICE".equals(i.getSource()));
        assertThat(page.getRecords()).allMatch(i -> Integer.valueOf(1).equals(i.getType()));
        assertThat(page.getRecords()).extracting(InboxItemDTO::getId)
                .containsExactlyInAnyOrder(N_UNREAD_SYS_1, N_UNREAD_SYS_2);
    }

    @Test
    @DisplayName("按类型筛选 type=3：仅返回已发布公告（2 条），不含站内信")
    void listInbox_filterTypeAnnouncement() {
        PageResult<InboxItemDTO> page = messageCenterService.listInbox(USER, 3, false, 1, 20);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).allMatch(i -> "ANNOUNCEMENT".equals(i.getSource()));
        assertThat(page.getRecords()).allMatch(i -> Integer.valueOf(3).equals(i.getType()));
        assertThat(page.getRecords()).extracting(InboxItemDTO::getId)
                .containsExactlyInAnyOrder(A_PUB_1, A_PUB_2);
    }

    @Test
    @DisplayName("仅未读 unreadOnly=true：仅返回未读站内信（2 条 type=1），已读/已删均排除")
    void listInbox_unreadOnly() {
        PageResult<InboxItemDTO> page = messageCenterService.listInbox(USER, null, true, 1, 20);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).allMatch(i -> "NOTICE".equals(i.getSource()));
        assertThat(page.getRecords()).allMatch(i -> Boolean.FALSE.equals(i.getRead()));
        assertThat(page.getRecords()).extracting(InboxItemDTO::getId)
                .containsExactlyInAnyOrder(N_UNREAD_SYS_1, N_UNREAD_SYS_2);
    }

    @Test
    @DisplayName("未读角标：total=2 / system=2 / interaction=0（公告不计入）")
    void unreadSummary_baseline() {
        UnreadSummaryVO vo = messageCenterService.unreadSummary(USER);

        assertThat(vo.getTotalUnread()).isEqualTo(2);
        assertThat(vo.getSystemUnread()).isEqualTo(2);
        assertThat(vo.getInteractionUnread()).isEqualTo(0);
    }

    @Test
    @DisplayName("标记单条已读后角标 -1；全部已读后角标归零（验证 @CacheEvict 生效）")
    void markRead_updatesSummaryAndEvictsCache() {
        // 初始角标
        assertThat(messageCenterService.unreadSummary(USER).getTotalUnread()).isEqualTo(2);

        // 标记一条已读：缓存应整体失效，重算后 = 1
        messageCenterService.markRead(USER, N_UNREAD_SYS_1);
        assertThat(messageCenterService.unreadSummary(USER).getTotalUnread()).isEqualTo(1);

        // 全部已读：角标归零
        messageCenterService.markAllRead(USER);
        UnreadSummaryVO after = messageCenterService.unreadSummary(USER);
        assertThat(after.getTotalUnread()).isEqualTo(0);
        assertThat(after.getSystemUnread()).isEqualTo(0);
        assertThat(after.getInteractionUnread()).isEqualTo(0);
    }
}

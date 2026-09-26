package com.moyue.message.center;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.common.cache.CacheNames;
import com.moyue.common.core.domain.PageResult;
import com.moyue.message.entity.NoticeEntity;
import com.moyue.message.mapper.NoticeMapper;
import com.moyue.message.service.MessageService;
import com.moyue.operation.entity.AnnouncementEntity;
import com.moyue.operation.mapper.AnnouncementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 消息中心服务：统一收件箱聚合 + 未读角标 + 按类型筛选 + 已读。
 *
 * <p>在已有的 {@code notice}（站内信）与 {@code announcement}（公告）之上做聚合，
 * 不改动既有通知分发链路。notice 无 {@code @TableLogic}，查询须显式 {@code is_deleted = 0}；
 * announcement 已有 {@code @TableLogic}，{@code selectList} 自动过滤已删。</p>
 *
 * <p>未读角标缓存于 {@link CacheNames#INBOX}，标记已读时主动失效，避免角标与列表不一致。</p>
 */
@Service
public class MessageCenterService {

    /** 系统消息类型 */
    private static final int TYPE_SYSTEM = 1;

    /** 互动消息类型 */
    private static final int TYPE_INTERACTION = 2;

    /** 公告类型 */
    private static final int TYPE_ANNOUNCEMENT = 3;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Autowired
    private MessageService messageService;

    /**
     * 统一收件箱：聚合当前用户的站内信与已发布公告，按时间倒序，内存分页返回。
     *
     * @param userId      用户 ID
     * @param type        类型筛选：null 表示全部；1 系统 / 2 互动 / 3 公告
     * @param unreadOnly  true 时仅返回未读站内信（公告恒为已读，不受此影响）
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @return 聚合后的分页结果
     */
    public PageResult<InboxItemDTO> listInbox(Long userId, Integer type, boolean unreadOnly, int page, int size) {
        List<InboxItemDTO> items = new ArrayList<>();

        // ① 站内信：显式 is_deleted = 0，DB 层按 create_time 倒序取全部（合并后再内存分页）
        IPage<NoticeEntity> noticePage = noticeMapper.selectInbox(
                new Page<>(1, Integer.MAX_VALUE), userId, type, unreadOnly);
        for (NoticeEntity n : noticePage.getRecords()) {
            items.add(toNoticeItem(n));
        }

        // ② 公告：仅当未限定类型或限定为公告（3）时纳入，@TableLogic 自动过滤已删
        if (type == null || type == TYPE_ANNOUNCEMENT) {
            List<AnnouncementEntity> announcements = announcementMapper.selectList(
                    new LambdaQueryWrapper<AnnouncementEntity>()
                            .eq(AnnouncementEntity::getStatus, 1)
                            .orderByDesc(AnnouncementEntity::getPublishTime)
                            .orderByDesc(AnnouncementEntity::getCreateTime));
            for (AnnouncementEntity a : announcements) {
                items.add(toAnnouncementItem(a));
            }
        }

        // ③ 仅未读：unreadOnly 作用于合并后的整体（公告恒为已读，自然被排除），
        //     与“仅看未读”的语义一致；notice 侧已在 DB 层预过滤，此处兜底整体过滤
        if (unreadOnly) {
            items = items.stream()
                    .filter(i -> Boolean.FALSE.equals(i.getRead()))
                    .collect(java.util.stream.Collectors.toList());
        }

        // ④ 合并后按 createTime 倒序（null 兜底排末尾），再内存分页
        items.sort(Comparator.comparing(InboxItemDTO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int total = items.size();
        int start = Math.min((page - 1) * size, total);
        int end = Math.min(start + size, total);
        List<InboxItemDTO> records = new ArrayList<>(items.subList(start, end));

        PageResult<InboxItemDTO> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setRecords(records);
        return result;
    }

    /**
     * 未读汇总（角标）。按 userId 维度缓存，标记已读时整体失效。
     *
     * @param userId 用户 ID
     * @return totalUnread / systemUnread / interactionUnread（公告不计入未读）
     */
    @Cacheable(cacheNames = CacheNames.INBOX, key = "#userId", unless = "#result == null")
    public UnreadSummaryVO unreadSummary(Long userId) {
        UnreadSummaryVO vo = new UnreadSummaryVO();
        vo.setTotalUnread(noticeMapper.countUnread(userId, List.of(TYPE_SYSTEM, TYPE_INTERACTION)));
        vo.setSystemUnread(noticeMapper.countUnread(userId, List.of(TYPE_SYSTEM)));
        vo.setInteractionUnread(noticeMapper.countUnread(userId, List.of(TYPE_INTERACTION)));
        return vo;
    }

    /**
     * 标记单条站内信已读：委托 {@link MessageService#markRead(Long, Long)}。
     * 该方法对越权（非本人）/ 不存在已抛异常（非静默），此处直接委托即可；
     * 标记后失效当前用户的未读角标缓存。
     *
     * @param userId   当前用户 ID
     * @param noticeId 站内信 ID
     */
    @CacheEvict(cacheNames = CacheNames.INBOX, key = "#userId")
    public void markRead(Long userId, Long noticeId) {
        messageService.markRead(noticeId, userId);
    }

    /**
     * 全部标记已读：委托 {@link MessageService#markAllRead(Long)}，返回影响行数；
     * 标记后失效当前用户的未读角标缓存。
     *
     * @param userId 当前用户 ID
     * @return 被置为已读的站内信条数
     */
    @CacheEvict(cacheNames = CacheNames.INBOX, key = "#userId")
    public int markAllRead(Long userId) {
        return messageService.markAllRead(userId);
    }

    private InboxItemDTO toNoticeItem(NoticeEntity n) {
        InboxItemDTO dto = new InboxItemDTO();
        dto.setSource("NOTICE");
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setTitle(n.getTitle());
        dto.setContent(n.getContent());
        dto.setRead(n.getIsRead() != null && n.getIsRead() == 1);
        dto.setCreateTime(n.getCreateTime());
        dto.setBizType(null);
        dto.setBizId(null);
        return dto;
    }

    private InboxItemDTO toAnnouncementItem(AnnouncementEntity a) {
        InboxItemDTO dto = new InboxItemDTO();
        dto.setSource("ANNOUNCEMENT");
        dto.setId(a.getId());
        dto.setType(TYPE_ANNOUNCEMENT);
        dto.setTitle(a.getTitle());
        dto.setContent(a.getContent());
        // 公告为平台全局广播，不计入未读
        dto.setRead(true);
        LocalDateTime time = a.getPublishTime() != null ? a.getPublishTime() : a.getCreateTime();
        dto.setCreateTime(time);
        dto.setBizType(null);
        dto.setBizId(null);
        return dto;
    }
}

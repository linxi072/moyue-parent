package com.moyue.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.moyue.common.BizException;
import com.moyue.common.ResultCode;
import com.moyue.message.entity.NoticeEntity;
import com.moyue.message.mapper.NoticeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 消息服务：站内通知查询、发送、标记已读（16-22）。
 *
 * <p>P2-14 由 moyue-social 整包迁入 moyue-message，包名零变更；T04 将扩展为渠道分发
 * （{@code MessageDispatcher} 复用本类的站内信写入能力）。</p>
 */
@Service
public class MessageService {

    /** 通知类型：1 系统 / 2 互动 / 3 公告（渠道分发默认走系统通知） */
    private static final int NOTICE_TYPE_SYSTEM = 1;

    @Autowired
    private NoticeMapper noticeMapper;

    /**
     * 查询指定用户的全部通知（按创建时间倒序）。
     *
     * @param unreadOnly true 时仅返回未读
     */
    public List<NoticeEntity> listByUser(Long userId, boolean unreadOnly) {
        LambdaQueryWrapper<NoticeEntity> wrapper = new LambdaQueryWrapper<NoticeEntity>()
                .eq(NoticeEntity::getUserId, userId)
                .orderByDesc(NoticeEntity::getCreateTime);
        if (unreadOnly) {
            wrapper.eq(NoticeEntity::getIsRead, 0);
        }
        return noticeMapper.selectList(wrapper);
    }

    /**
     * 发送一条站内通知（默认未读、未删除）。
     */
    public void send(NoticeEntity notice) {
        notice.setCreateTime(LocalDateTime.now());
        notice.setIsRead(0);
        notice.setIsDeleted(0);
        noticeMapper.insert(notice);
    }

    /**
     * 分发入口：以「系统通知」类型向指定用户写一条站内信（供 {@code InboxChannelSender}
     * 与其它触达链路复用，避免各处重复拼装 {@link NoticeEntity}）。
     *
     * @param userId  接收用户 ID
     * @param title   标题
     * @param content 正文
     */
    public void sendInbox(Long userId, String title, String content) {
        NoticeEntity notice = new NoticeEntity();
        notice.setUserId(userId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setType(NOTICE_TYPE_SYSTEM);
        send(notice);
    }

    /**
     * 标记单条已读（16-22）：通知必须存在且属于当前用户（防越权改他人通知）。
     * 已读重复标记保持幂等（直接返回成功）。
     */
    public void markRead(Long noticeId, Long userId) {
        NoticeEntity notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BizException(ResultCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        if (!Objects.equals(notice.getUserId(), userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作他人通知");
        }
        if (notice.getIsRead() != null && notice.getIsRead() == 1) {
            return;
        }
        noticeMapper.update(null, new LambdaUpdateWrapper<NoticeEntity>()
                .eq(NoticeEntity::getId, noticeId)
                .set(NoticeEntity::getIsRead, 1));
    }

    /**
     * 全部标记已读（16-22）：数据库层条件原子更新，返回影响行数。
     */
    public int markAllRead(Long userId) {
        return noticeMapper.update(null, new LambdaUpdateWrapper<NoticeEntity>()
                .eq(NoticeEntity::getUserId, userId)
                .eq(NoticeEntity::getIsRead, 0)
                .set(NoticeEntity::getIsRead, 1));
    }
}

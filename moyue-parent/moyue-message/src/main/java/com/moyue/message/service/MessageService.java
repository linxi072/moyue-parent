package com.moyue.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.message.entity.NoticeEntity;
import com.moyue.message.mapper.NoticeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息服务：站内通知查询与发送。
 */
@Service
public class MessageService {

    @Autowired
    private NoticeMapper noticeMapper;

    /**
     * 查询指定用户的全部通知（按创建时间倒序）。
     */
    public List<NoticeEntity> listByUser(Long userId) {
        return noticeMapper.selectList(new LambdaQueryWrapper<NoticeEntity>()
                .eq(NoticeEntity::getUserId, userId)
                .orderByDesc(NoticeEntity::getCreateTime));
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
}

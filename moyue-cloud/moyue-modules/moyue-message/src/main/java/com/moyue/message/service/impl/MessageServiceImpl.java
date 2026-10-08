package com.moyue.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.message.domain.dto.query.MessageQuery;
import com.moyue.message.domain.entity.Message;
import com.moyue.message.domain.entity.MessageTemplate;
import com.moyue.message.domain.vo.MessageVO;
import com.moyue.message.mapper.MessageMapper;
import com.moyue.message.mapper.MessageTemplateMapper;
import com.moyue.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息域（站内信）实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final MessageTemplateMapper templateMapper;

    @Override
    public PageResult<MessageVO> pageMessages(MessageQuery query) {
        var page = PageUtils.<Message>page(query);
        var result = messageMapper.selectPage(page, new LambdaQueryWrapper<Message>()
                .like(StringUtils.hasText(query.getTitle()), Message::getTitle, query.getTitle())
                .eq(query.getToUser() != null, Message::getToUser, query.getToUser())
                .eq(query.getType() != null, Message::getType, query.getType())
                .eq(query.getReadFlag() != null, Message::getReadFlag, query.getReadFlag())
                .orderByDesc(Message::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMessage(Message entity) {
        if (!StringUtils.hasText(entity.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息标题不能为空");
        }
        if (entity.getType() == null) {
            entity.setType(1);
        }
        if (entity.getReadFlag() == null) {
            entity.setReadFlag(0);
        }
        messageMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMessage(Message entity) {
        Message exist = messageMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("站内信");
        }
        return messageMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(Long id) {
        Message exist = messageMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("站内信");
        }
        return messageMapper.deleteById(id) > 0;
    }

    @Override
    public long unreadCount(Long toUser) {
        if (toUser == null) {
            return 0L;
        }
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getToUser, toUser)
                .eq(Message::getReadFlag, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendOne(Long toUser, String title, String content, Integer type, String templateCode, String name) {
        if (toUser == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "接收人不能为空");
        }
        return doSend(toUser, title, content, type, templateCode, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> sendBatch(List<Long> toUserIds, String title, String content, Integer type, String templateCode, String name) {
        if (toUserIds == null || toUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "接收人列表不能为空");
        }
        List<Long> ids = new ArrayList<>(toUserIds.size());
        for (Long toUser : toUserIds) {
            ids.add(doSend(toUser, title, content, type, templateCode, name));
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean readAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        Message upd = new Message();
        upd.setReadFlag(1);
        return messageMapper.update(upd, new LambdaQueryWrapper<Message>().in(Message::getId, ids)) > 0;
    }

    /** 落库单条未读消息；templateCode 非空时引用启用中模板并渲染 ${name} */
    private Long doSend(Long toUser, String title, String content, Integer type, String templateCode, String name) {
        String finalTitle = title;
        String finalContent = content;
        if (StringUtils.hasText(templateCode)) {
            MessageTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                    .eq(MessageTemplate::getCode, templateCode)
                    .eq(MessageTemplate::getEnabled, 1));
            if (t != null) {
                finalTitle = t.getTitle();
                finalContent = render(t.getContent(), name);
            }
        }
        Message m = new Message();
        m.setFromUser(0L);
        m.setToUser(toUser);
        m.setTitle(finalTitle);
        m.setContent(finalContent);
        m.setType(type == null ? 1 : type);
        m.setReadFlag(0);
        messageMapper.insert(m);
        return m.getId();
    }

    private String render(String template, String name) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        String n = StringUtils.hasText(name) ? name : "用户";
        return template.replace("${name}", n);
    }

    private MessageVO toVO(Message e) {
        return MessageVO.builder()
                .id(e.getId())
                .fromUser(e.getFromUser())
                .toUser(e.getToUser())
                .title(e.getTitle())
                .content(e.getContent())
                .type(e.getType())
                .readFlag(e.getReadFlag())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}

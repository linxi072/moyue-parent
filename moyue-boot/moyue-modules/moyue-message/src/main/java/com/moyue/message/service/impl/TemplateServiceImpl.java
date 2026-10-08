package com.moyue.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.common.core.result.PageResult;
import com.moyue.common.mybatis.util.PageUtils;
import com.moyue.message.domain.dto.query.MessageTemplateQuery;
import com.moyue.message.domain.entity.MessageTemplate;
import com.moyue.message.domain.vo.MessageTemplateVO;
import com.moyue.message.mapper.MessageTemplateMapper;
import com.moyue.message.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 消息模板服务实现。
 *
 * @author moyue
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final MessageTemplateMapper templateMapper;

    @Override
    public PageResult<MessageTemplateVO> pageTemplates(MessageTemplateQuery query) {
        var page = PageUtils.<MessageTemplate>page(query);
        var result = templateMapper.selectPage(page, new LambdaQueryWrapper<MessageTemplate>()
                .and(StringUtils.hasText(query.getKeyword()),
                        w -> w.like(MessageTemplate::getCode, query.getKeyword())
                                .or().like(MessageTemplate::getTitle, query.getKeyword()))
                .eq(query.getType() != null, MessageTemplate::getType, query.getType())
                .eq(query.getEnabled() != null, MessageTemplate::getEnabled, query.getEnabled())
                .orderByDesc(MessageTemplate::getCreateTime));
        return PageUtils.toResult(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTemplate(MessageTemplate entity) {
        if (!StringUtils.hasText(entity.getCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模板编码不能为空");
        }
        if (!StringUtils.hasText(entity.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模板标题不能为空");
        }
        MessageTemplate exist = templateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getCode, entity.getCode()));
        if (exist != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模板编码已存在：" + entity.getCode());
        }
        if (entity.getType() == null) {
            entity.setType(1);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        templateMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTemplate(MessageTemplate entity) {
        MessageTemplate exist = templateMapper.selectById(entity.getId());
        if (exist == null) {
            throw BusinessException.notFound("消息模板");
        }
        return templateMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTemplate(Long id) {
        MessageTemplate exist = templateMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("消息模板");
        }
        return templateMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enable(Long id) {
        return setEnabled(id, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id) {
        return setEnabled(id, 0);
    }

    @Override
    public MessageTemplateVO getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        MessageTemplate t = templateMapper.selectOne(new LambdaQueryWrapper<MessageTemplate>()
                .eq(MessageTemplate::getCode, code)
                .eq(MessageTemplate::getEnabled, 1));
        return t == null ? null : toVO(t);
    }

    private boolean setEnabled(Long id, int enabled) {
        MessageTemplate exist = templateMapper.selectById(id);
        if (exist == null) {
            throw BusinessException.notFound("消息模板");
        }
        MessageTemplate upd = new MessageTemplate();
        upd.setId(id);
        upd.setEnabled(enabled);
        return templateMapper.updateById(upd) > 0;
    }

    private MessageTemplateVO toVO(MessageTemplate e) {
        return MessageTemplateVO.builder()
                .id(e.getId())
                .code(e.getCode())
                .title(e.getTitle())
                .content(e.getContent())
                .type(e.getType())
                .enabled(e.getEnabled())
                .createTime(e.getCreateTime())
                .remark(e.getRemark())
                .build();
    }
}

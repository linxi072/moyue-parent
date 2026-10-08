package com.moyue.message.service;

import com.moyue.common.core.result.PageResult;
import com.moyue.message.domain.dto.query.MessageTemplateQuery;
import com.moyue.message.domain.entity.MessageTemplate;
import com.moyue.message.domain.vo.MessageTemplateVO;

/**
 * 消息模板服务：CRUD + 启用 / 停用 + 按编码查询。
 *
 * @author moyue
 */
public interface TemplateService {

    PageResult<MessageTemplateVO> pageTemplates(MessageTemplateQuery query);

    Long createTemplate(MessageTemplate entity);

    boolean updateTemplate(MessageTemplate entity);

    boolean deleteTemplate(Long id);

    boolean enable(Long id);

    boolean disable(Long id);

    /** 按编码查询启用中的模板（群发渲染用） */
    MessageTemplateVO getByCode(String code);
}

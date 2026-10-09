package com.moyue.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyue.common.core.exception.BusinessException;
import com.moyue.common.core.exception.ErrorCode;
import com.moyue.message.domain.entity.MessageTemplate;
import com.moyue.message.domain.vo.MessageTemplateVO;
import com.moyue.message.mapper.MessageTemplateMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息模板服务单元测试（Mockito，无 Spring 上下文）。
 *
 * <p>覆盖：编码/标题校验、编码去重、默认值（启用）、启用/停用、按编码取模板。
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock
    private MessageTemplateMapper templateMapper;

    @InjectMocks
    private TemplateServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "init");
        TableInfoHelper.initTableInfo(assistant, MessageTemplate.class);
    }

    private static MessageTemplate template(String code, String title, String content) {
        MessageTemplate t = new MessageTemplate();
        t.setId(1L);
        t.setCode(code);
        t.setTitle(title);
        t.setContent(content);
        t.setEnabled(1);
        return t;
    }

    @Test
    void createTemplate_blankCode_throwsParamError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createTemplate(new MessageTemplate()));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void createTemplate_duplicateCode_throwsParamError() {
        MessageTemplate dup = template("NOTICE", "x", "内容");
        when(templateMapper.selectOne(any())).thenReturn(dup);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTemplate(dup));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void createTemplate_defaultsEnabledAndInserts() {
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(templateMapper.insert(any(MessageTemplate.class))).thenReturn(1);

        MessageTemplate bare = new MessageTemplate();
        bare.setCode("NEW");
        bare.setTitle("新模板");
        service.createTemplate(bare);

        ArgumentCaptor<MessageTemplate> cap = ArgumentCaptor.forClass(MessageTemplate.class);
        verify(templateMapper).insert(cap.capture());
        assertEquals(1, cap.getValue().getEnabled());
    }

    @Test
    void enable_notFound_throwsNotFound() {
        when(templateMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.enable(1L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void enable_setsEnabled() {
        when(templateMapper.selectById(1L)).thenReturn(template("A", "a", "x"));
        when(templateMapper.updateById(any(MessageTemplate.class))).thenReturn(1);

        assertTrue(service.enable(1L));

        ArgumentCaptor<MessageTemplate> cap = ArgumentCaptor.forClass(MessageTemplate.class);
        verify(templateMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getEnabled());
    }

    @Test
    void getByCode_blank_returnsNull() {
        assertNull(service.getByCode("  "));
        verify(templateMapper, never()).selectOne(any());
    }

    @Test
    void getByCode_enabled_returnsVO() {
        when(templateMapper.selectOne(any())).thenReturn(template("A", "a", "x"));
        MessageTemplateVO vo = service.getByCode("A");
        assertNotNull(vo);
        assertEquals("A", vo.getCode());
    }
}

package com.moyue.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyue.message.channel.MessageChannel;
import com.moyue.message.entity.MessageTemplateEntity;
import com.moyue.message.mapper.MessageTemplateMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息模板服务（P2-14）：按 {@code templateCode} 取模板并渲染占位符。
 *
 * <p>占位符同时支持 {@code {key}} 与 {@code ${key}} 两种写法，按 {@code params} 做简单字符串替换；
 * 模板缺失时退化处理（标题 / 正文取模板编码，默认仅站内信渠道），保证分发链路不阻断。</p>
 */
@Service
public class MessageTemplateService {

    /** 未指定渠道时的默认渠道：仅站内信 */
    private static final List<Integer> DEFAULT_CHANNELS = List.of(MessageChannel.INBOX.getCode());

    /** 模板启用状态 */
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private MessageTemplateMapper messageTemplateMapper;

    /**
     * 渲染模板。
     *
     * @param code   模板编码；为空 / 不存在时走退化分支
     * @param params 占位符参数
     * @return 渲染结果（标题 / 正文 / 渠道）
     */
    public Template render(String code, Map<String, String> params) {
        MessageTemplateEntity entity = null;
        if (code != null && !code.isBlank()) {
            entity = messageTemplateMapper.selectOne(new LambdaQueryWrapper<MessageTemplateEntity>()
                    .eq(MessageTemplateEntity::getCode, code)
                    .eq(MessageTemplateEntity::getStatus, STATUS_ENABLED)
                    .last("LIMIT 1"));
        }
        if (entity == null) {
            // 无模板：退化使用编码作为标题 + 正文，默认渠道仅站内信（简化实现，保证分发不阻断）
            String fallback = (code == null || code.isBlank()) ? "系统通知" : code;
            return new Template(code, fallback, fallback, DEFAULT_CHANNELS);
        }
        return new Template(code,
                renderText(entity.getTitleTpl(), params),
                renderText(entity.getContentTpl(), params),
                parseChannels(entity.getChannels()));
    }

    /** 占位符替换：依次处理 {@code ${key}} 与 {@code {key}}，兼容两种写法 */
    private String renderText(String template, Map<String, String> params) {
        if (template == null) {
            return null;
        }
        String result = template;
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                String value = entry.getValue() == null ? "" : entry.getValue();
                result = result.replace("${" + key + "}", value).replace("{" + key + "}", value);
            }
        }
        return result;
    }

    /** 解析逗号分隔渠道串，如 "1,2" → [1,2]；为空 / 全非法时回退默认渠道 */
    private List<Integer> parseChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return DEFAULT_CHANNELS;
        }
        List<Integer> list = new ArrayList<>();
        for (String part : channels.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                list.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // 忽略非法渠道段
            }
        }
        return list.isEmpty() ? DEFAULT_CHANNELS : list;
    }

    /** 渲染结果载体：编码 / 标题 / 正文 / 渠道列表 */
    @Data
    @AllArgsConstructor
    public static class Template {

        /** 模板编码 */
        private String code;

        /** 渲染后的标题 */
        private String title;

        /** 渲染后的正文 */
        private String content;

        /** 目标渠道码列表（1/2/3/4） */
        private List<Integer> channels;
    }
}

package com.moyue.message.service;

import com.moyue.api.message.dto.MessageDispatchDTO;
import com.moyue.api.message.dto.MessageDispatchResultDTO;
import com.moyue.message.channel.ChannelMessage;
import com.moyue.message.channel.ChannelSendResult;
import com.moyue.message.channel.ChannelSender;
import com.moyue.message.channel.MessageChannel;
import com.moyue.message.entity.MessageChannelRecordEntity;
import com.moyue.message.mapper.MessageChannelRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息分发器（P2-14 核心）：按 {@link MessageDispatchDTO#getChannels()} 逐渠道路由到对应
 * {@link ChannelSender}（{@code senders.stream().filter(s -> s.channel() == c)}）。
 *
 * <p>关键约定：</p>
 * <ul>
 *   <li>逐渠道 try/catch —— 单一渠道失败不影响其它渠道；</li>
 *   <li>每条结果写 {@code message_channel_record}（含 status / pending、detail）；</li>
 *   <li>返回 {@link MessageDispatchResultDTO}（含各渠道明细）。</li>
 * </ul>
 */
@Slf4j
@Service
public class MessageDispatcher {

    /** 触达记录状态：0 待发（含 pending）/ 1 成功 / 2 失败 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;

    @Autowired
    private List<ChannelSender> channelSenders;

    @Autowired
    private MessageTemplateService messageTemplateService;

    @Autowired
    private MessageChannelRecordMapper messageChannelRecordMapper;

    /**
     * 执行一次分发：渲染模板 → 逐渠道发送 → 落触达记录 → 汇总结果。
     *
     * @param dto 触达请求（channels 为空时取模板默认渠道）
     * @return 汇总结果（total / success / failed / channelDetails）
     */
    public MessageDispatchResultDTO dispatch(MessageDispatchDTO dto) {
        MessageTemplateService.Template template =
                messageTemplateService.render(dto.getTemplateCode(), dto.getParams());

        List<Integer> channels = (dto.getChannels() != null && !dto.getChannels().isEmpty())
                ? dto.getChannels()
                : template.getChannels();

        MessageDispatchResultDTO result = new MessageDispatchResultDTO();
        List<String> details = new ArrayList<>();
        int total = 0;
        int success = 0;
        int failed = 0;

        for (Integer channelCode : channels) {
            MessageChannel channel = MessageChannel.fromCode(channelCode);
            if (channel == null) {
                details.add("UNKNOWN(" + channelCode + ")=SKIP");
                continue;
            }
            total++;
            ChannelMessage message = buildChannelMessage(dto, template, channel);
            ChannelSendResult sendResult = doSend(channel, message);
            saveRecord(dto, channel, message, sendResult);

            if (sendResult.isSuccess()) {
                success++;
                details.add(channel.name() + "=OK");
            } else if (sendResult.isPending()) {
                details.add(channel.name() + "=PENDING " + sendResult.getDetail());
            } else {
                failed++;
                details.add(channel.name() + "=FAILED " + sendResult.getDetail());
            }
        }

        result.setTotal(total);
        result.setSuccess(success);
        result.setFailed(failed);
        result.setChannelDetails(details);
        return result;
    }

    /** 路由到指定渠道并发送；渠道未注册或抛异常时转为失败结果（逐渠道隔离） */
    private ChannelSendResult doSend(MessageChannel channel, ChannelMessage message) {
        ChannelSender sender = channelSenders.stream()
                .filter(s -> s.channel() == channel)
                .findFirst()
                .orElse(null);
        if (sender == null) {
            return ChannelSendResult.failure("渠道未注册：" + channel);
        }
        try {
            return sender.send(message);
        } catch (Exception ex) {
            log.warn("渠道 {} 发送异常 userId={}, err={}", channel, message.getUserId(), ex.getMessage());
            return ChannelSendResult.failure("渠道异常：" + ex.getMessage());
        }
    }

    /** 组装渠道消息载体（target 按渠道映射 email / phone） */
    private ChannelMessage buildChannelMessage(MessageDispatchDTO dto,
                                               MessageTemplateService.Template template,
                                               MessageChannel channel) {
        ChannelMessage message = new ChannelMessage();
        message.setUserId(dto.getUserId());
        message.setChannel(channel);
        message.setTemplateCode(dto.getTemplateCode());
        message.setTitle(template.getTitle());
        message.setContent(template.getContent());
        message.setBizType(dto.getBizType());
        message.setBizId(dto.getBizId());
        if (channel == MessageChannel.EMAIL) {
            message.setTarget(dto.getTargetEmail());
        } else if (channel == MessageChannel.SMS) {
            message.setTarget(dto.getTargetPhone());
        }
        return message;
    }

    /** 写触达记录；记录失败不影响分发主流程 */
    private void saveRecord(MessageDispatchDTO dto, MessageChannel channel,
                            ChannelMessage message, ChannelSendResult sendResult) {
        try {
            MessageChannelRecordEntity record = new MessageChannelRecordEntity();
            record.setUserId(dto.getUserId());
            record.setChannel(channel.getCode());
            record.setTemplateCode(dto.getTemplateCode());
            record.setTarget(message.getTarget());
            record.setTitle(message.getTitle());
            record.setContent(message.getContent());
            record.setBizType(dto.getBizType());
            record.setBizId(dto.getBizId());
            if (sendResult.isSuccess()) {
                record.setStatus(STATUS_SUCCESS);
                record.setErrorMsg(null);
                record.setSendTime(LocalDateTime.now());
            } else if (sendResult.isPending()) {
                record.setStatus(STATUS_PENDING);
                record.setErrorMsg(sendResult.getDetail());
            } else {
                record.setStatus(STATUS_FAILED);
                record.setErrorMsg(sendResult.getDetail());
            }
            record.setRetryCount(0);
            record.setIsDeleted(0);
            LocalDateTime now = LocalDateTime.now();
            record.setCreateTime(now);
            record.setUpdateTime(now);
            messageChannelRecordMapper.insert(record);
        } catch (Exception ex) {
            log.warn("写入触达记录失败 userId={}, channel={}, err={}",
                    dto.getUserId(), channel, ex.getMessage());
        }
    }
}

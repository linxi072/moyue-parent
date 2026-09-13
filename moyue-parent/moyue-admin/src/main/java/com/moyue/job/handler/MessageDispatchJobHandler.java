package com.moyue.job.handler;

import com.moyue.api.client.MessageDispatchClient;
import com.moyue.api.dto.MessageDispatchDTO;
import com.moyue.api.dto.MessageDispatchResultDTO;
import com.moyue.common.R;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 触达定时任务 Handler（P2-14 N-5）。
 *
 * <p>不新增执行器：复用 platform 唯一 XXL-Job 执行器（{@code appname=moyue-job}），
 * 经 {@link MessageDispatchClient} 调 moyue-message 做一批触达（此处取「未读汇总」的合理简化实现，
 * 调度参数可传 userId；缺省 userId=1）。失败仅记日志，不阻断调度。</p>
 */
@Component
public class MessageDispatchJobHandler {

    /** 系统未读汇总模板编码（可由运营在 message_template 维护；缺失时模板服务自动兜底） */
    private static final String TEMPLATE_UNREAD_DIGEST = "UNREAD_DIGEST";

    /** 缺省触达对象用户 ID */
    private static final long DEFAULT_USER_ID = 1L;

    @Autowired(required = false)
    private MessageDispatchClient messageDispatchClient;

    /**
     * 消息触达任务：调度参数可选，为 userId；未传时对缺省用户做一次未读汇总触达。
     */
    @XxlJob("messageDispatchJob")
    public void messageDispatchJob() {
        long userId = parseUserId(XxlJobHelper.getJobParam());

        Map<String, String> params = new HashMap<>();
        params.put("userId", String.valueOf(userId));
        params.put("digestDate", LocalDate.now().toString());

        MessageDispatchDTO dto = new MessageDispatchDTO();
        dto.setUserId(userId);
        dto.setTemplateCode(TEMPLATE_UNREAD_DIGEST);
        dto.setParams(params);
        dto.setBizType("SYSTEM");
        dto.setBizId(userId);

        if (messageDispatchClient == null) {
            XxlJobHelper.log("moyue-message 未注册到 Nacos，跳过本次触达");
            XxlJobHelper.handleSuccess("messageDispatchJob skipped");
            return;
        }
        try {
            R<MessageDispatchResultDTO> resp = messageDispatchClient.dispatch(dto);
            XxlJobHelper.log("触达任务 userId={} 返回 code={}, data={}", userId, resp.getCode(), resp.getData());
            XxlJobHelper.handleSuccess("messageDispatchJob done");
        } catch (Exception ex) {
            // 失败仅记日志，不阻断调度
            XxlJobHelper.log("触达任务调用 moyue-message 失败（已降级）：{}", ex.getMessage());
            XxlJobHelper.handleSuccess("messageDispatchJob degraded");
        }
    }

    /** 解析调度参数为 userId；非法 / 缺失回退缺省值 */
    private long parseUserId(String param) {
        if (param != null) {
            String trimmed = param.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return Long.parseLong(trimmed);
                } catch (NumberFormatException ignored) {
                    // 非法参数：回退缺省值
                }
            }
        }
        return DEFAULT_USER_ID;
    }
}

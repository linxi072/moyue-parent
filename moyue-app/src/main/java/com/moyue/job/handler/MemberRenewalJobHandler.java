package com.moyue.job.handler;

import com.moyue.api.member.client.MemberClient;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 会员连续订阅（自动续费）调度 Handler。
 *
 * <p>定时扫描「生效中(status=1) + 已开自动续费 + renew_at 已过」的订阅，经 {@link MemberClient}
 * 调会员服务逐条续费（扣款 + 整期延长 endTime）。底层 {@code renewDueSubscriptions} 为分页扫描，
 * 单条失败仅记日志、标记续费失败并保持 active，不波及他人、不抛未捕获异常，重复调度幂等无副作用。</p>
 *
 * <p>复用 platform 唯一 XXL-Job 执行器（{@code appname=moyue-job}），经 {@link MemberClient} 调会员服务。
 * 会员服务未注册时安全降级，失败仅记日志、不阻断调度（下一轮扫描重新命中）。</p>
 *
 * <p>部署：需在 XXL-Job Admin 控制台注册 {@code memberRenewalJob}，cron 建议每 30 分钟（如 {@code 0 0/30 * * * ?}）。</p>
 */
@Slf4j
@Component
public class MemberRenewalJobHandler {

    @Autowired(required = false)
    private MemberClient memberClient;

    @XxlJob("memberRenewalJob")
    public void memberRenewalJob() {
        if (memberClient == null) {
            XxlJobHelper.log("会员服务未注册，跳过本次自动续费扫描");
            XxlJobHelper.handleSuccess("memberRenewalJob skipped");
            return;
        }
        try {
            memberClient.renewDueSubscriptions();
            XxlJobHelper.log("会员自动续费扫描完成");
            XxlJobHelper.handleSuccess("memberRenewalJob done");
        } catch (Exception e) {
            // 失败仅记日志，不阻断调度（下轮重新扫描待续费订阅）
            log.error("会员续费任务异常", e);
            XxlJobHelper.log("会员自动续费扫描异常（已降级，下轮重试）：{}", e.getMessage());
            XxlJobHelper.handleSuccess("memberRenewalJob degraded");
        }
    }
}

package com.moyue.job.handler;

import com.moyue.api.member.client.MemberClient;
import com.moyue.common.R;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 会员订阅到期自动降级调度 Handler。
 *
 * <p>定时扫描「生效中(status=1) 且 end_time 已过」的订阅翻为「已过期(status=2)」，
 * 使会员权益（免广告 / 折扣 / 徽章）在到期后自动收回。底层 {@code syncExpired} 为条件更新，
 * 仅命中过期行，重复调度幂等无副作用。</p>
 *
 * <p>复用 platform 唯一 XXL-Job 执行器（{@code appname=moyue-job}），经 {@link MemberClient} 调会员服务。
 * 会员服务未注册时安全降级，失败仅记日志、不阻断调度（下一轮扫描重新命中）。</p>
 *
 * <p>部署：需在 XXL-Job Admin 控制台注册 {@code memberExpireJob}，cron 建议每日一次（如 {@code 0 0 3 * * ?}）。</p>
 */
@Component
public class MemberExpireJobHandler {

    @Autowired(required = false)
    private MemberClient memberClient;

    @XxlJob("memberExpireJob")
    public void memberExpireJob() {
        if (memberClient == null) {
            XxlJobHelper.log("会员服务未注册，跳过本次订阅到期扫描");
            XxlJobHelper.handleSuccess("memberExpireJob skipped");
            return;
        }
        try {
            R<Integer> resp = memberClient.syncExpired();
            int expired = (resp == null || resp.getData() == null) ? 0 : resp.getData();
            XxlJobHelper.log("会员订阅到期扫描完成，本次降级 {} 条", expired);
            XxlJobHelper.handleSuccess("memberExpireJob done");
        } catch (Exception ex) {
            // 失败仅记日志，不阻断调度（下轮重新扫描过期订阅）
            XxlJobHelper.log("会员订阅到期扫描失败（已降级，下轮重试）：{}", ex.getMessage());
            XxlJobHelper.handleSuccess("memberExpireJob degraded");
        }
    }
}

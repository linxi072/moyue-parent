package com.moyue.job.handler;

import com.moyue.api.content.client.ChapterClient;
import com.moyue.common.R;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 章节定时发布调度 Handler（P0-2）。
 *
 * <p>把「定时待发布(status=4) 且 publish_time 已到点」的章节激活为「已发布(status=2)」，
 * 并由 moyue-content 侧复用既有的索引同步 hook 写入 ES。
 * 激活逻辑自带 {@code status=4} 条件更新，重复调度幂等；多实例并发下由数据库条件更新保证只成功一次。</p>
 *
 * <p>不新增执行器：复用 platform 唯一 XXL-Job 执行器（{@code appname=moyue-job}），
 * 经 {@link ChapterClient} 调 moyue-content 内部端点。moyue-content 未注册时安全降级，
 * 失败仅记日志，不阻断调度。</p>
 *
 * <p>部署：需在 XXL-Job Admin 控制台注册 {@code chapterPublishJob}，cron 建议每 1 分钟一次。</p>
 */
@Component
public class ChapterPublishJobHandler {

    @Autowired(required = false)
    private ChapterClient chapterClient;

    @XxlJob("chapterPublishJob")
    public void chapterPublishJob() {
        if (chapterClient == null) {
            XxlJobHelper.log("moyue-content 未注册到 Nacos，跳过本次定时章节激活");
            XxlJobHelper.handleSuccess("chapterPublishJob skipped");
            return;
        }
        try {
            R<Integer> resp = chapterClient.activateScheduledChapters();
            Integer activated = resp == null ? null : resp.getData();
            XxlJobHelper.log("定时章节激活完成，本次激活 {} 条", activated == null ? 0 : activated);
            XxlJobHelper.handleSuccess("chapterPublishJob done");
        } catch (Exception ex) {
            // 失败仅记日志，不阻断调度（下一轮扫描会重新命中未激活的章节）
            XxlJobHelper.log("定时章节激活失败（已降级，下轮重试）：{}", ex.getMessage());
            XxlJobHelper.handleSuccess("chapterPublishJob degraded");
        }
    }
}

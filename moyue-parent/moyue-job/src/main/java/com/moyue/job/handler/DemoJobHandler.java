package com.moyue.job.handler;

import com.moyue.api.client.BookClient;
import com.moyue.api.client.CommentClient;
import com.moyue.api.client.UserClient;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 示例任务 Handler。
 * 演示三种常见定时任务写法：简单任务、分片广播、跨服务聚合统计。
 * 在 XXL-Job 调度中心按 @XxlJob 的值注册执行器任务即可触发。
 */
@Component
public class DemoJobHandler {

    @Autowired(required = false)
    private BookClient bookClient;

    @Autowired(required = false)
    private CommentClient commentClient;

    @Autowired(required = false)
    private UserClient userClient;

    /** 1) Hello World：最简单任务 */
    @XxlJob("demoJobHandler")
    public void demoJobHandler() {
        XxlJobHelper.log("demoJobHandler 执行，时间={}", System.currentTimeMillis());
        XxlJobHelper.handleSuccess("demoJobHandler done");
    }

    /** 2) 分片广播示例：多实例部署时按 shardIndex / shardTotal 分片处理 */
    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        XxlJobHelper.log("分片参数：当前分片={}, 总分片={}", shardIndex, shardTotal);
        // 实际场景：按 (id % shardTotal) == shardIndex 取数据分片处理
        XxlJobHelper.handleSuccess("sharding done");
    }

    /**
     * 3) 跨服务聚合统计：演示 Job 通过 Feign 调用各业务服务汇总。
     * 目标服务未注册到 Nacos 时安全降级，不阻断任务。
     */
    @XxlJob("statAggregateJobHandler")
    public void statAggregateJobHandler() {
        long bookCount = safe(() -> bookClient.listBooks(1, 1).getData().getTotal(), "book");
        long commentCount = safe(() -> commentClient.listComments(1L, 1, 1).getData().getTotal(), "comment");
        long userId = safe(() -> userClient.getUser(1L).getData().getId(), "user");
        XxlJobHelper.log("聚合统计：书籍={}, 评论={}, 用户={}", bookCount, commentCount, userId);
        XxlJobHelper.handleSuccess("stat aggregated");
    }

    /** 安全调用：目标服务不可用时不阻断任务，返回 0 */
    private long safe(Supplier<Long> call, String name) {
        try {
            Long v = call.get();
            return v == null ? 0L : v;
        } catch (Exception e) {
            XxlJobHelper.log("调用 {} 服务失败（可能未启动）：{}", name, e.getMessage());
            return 0L;
        }
    }
}

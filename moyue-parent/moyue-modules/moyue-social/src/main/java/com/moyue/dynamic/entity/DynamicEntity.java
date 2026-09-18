package com.moyue.dynamic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户动态实体，映射 user_dynamic 表（P2-E）。
 *
 * <p>isDeleted 显式 {@code @TableLogic(value="0", delval="1")}：项目全局逻辑删除未启用，
 * 不写会静默物理删。动态经 uk_dynamic_ref(ref_type, ref_id) 幂等落库（重发布走 upsertByRef 复活 + 刷新快照）。</p>
 */
@Data
@TableName("user_dynamic")
public class DynamicEntity {

    /** 主键（雪花 ID） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 动作发起者（打赏者 / 作者本人）ID → user.id（可空：系统类动态无发起者） */
    private Long actorUserId;

    /** 反规范化快照：动作发起者昵称 */
    private String actorName;

    /** 动态归属作者（进粉丝流的人）ID → user.id */
    private Long authorId;

    /** 反规范化快照：作者昵称 */
    private String authorName;

    /** 关联作品 ID → book.id */
    private Long bookId;

    /** 反规范化快照：作品标题 */
    private String bookTitle;

    /** 动态类型：1 发布新作 / 2 作品完结 / 3 打赏 / 4 关注(预留) */
    private Integer dynamicType;

    /** 可选补充文案 */
    private String summary;

    /** 来源业务主键（bookId / rewardOrderId） */
    private Long refId;

    /** 来源类型：1=book发布 / 2=reward订单 */
    private Integer refType;

    /** 逻辑删除：0 否 / 1 是 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    /** 创建时间（排序键） */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
